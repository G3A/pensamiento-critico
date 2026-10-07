package pensamiento.nucleo.reglas;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import pensamiento.nucleo.Argumento;
import pensamiento.nucleo.EstadoAfirmacion;
import pensamiento.nucleo.EstandarPrueba;

/**
 * R04 · Aceptabilidad bajo estándar de prueba (modelo de Carneades sobre la tabla argumento).
 * Una premisa cuenta como aceptada si su afirmación está verificada, o si está marcada asumible y nadie
 * la cuestionó, o si es no verificable pero el usuario la adoptó como premisa de valor. Una premisa
 * cuestionada (disputada, o asumible con objeción) bloquea el argumento salvo en escrutinio. Un argumento
 * cuya conclusión es premisa de sí mismo por cualquier camino no aporta peso.
 */
public final class R04Aceptabilidad {

    public record Parametros(int umbralMayorProClaroYConvincente, int umbralContraMaximoMasAllaDeDuda) {
        public static Parametros v1() {
            return new Parametros(3, 1);
        }
    }

    /** Lo que R04 necesita saber de cada afirmación que aparece como premisa. */
    public record EstadoPremisa(EstadoAfirmacion estado, boolean adoptadaComoPremisaDeValor, boolean conObjecion) {
        public static EstadoPremisa de(EstadoAfirmacion estado) {
            return new EstadoPremisa(estado, false, false);
        }
    }

    enum Juicio { ACEPTADA, CUESTIONADA, NO_ACEPTADA }

    private R04Aceptabilidad() {
    }

    /**
     * @param conclusion   afirmación que se evalúa
     * @param estandar     estándar de prueba exigido
     * @param argumentos   todos los argumentos del grafo del usuario (pro y contra, de cualquier conclusión)
     * @param premisas     estado de cada afirmación que aparece como premisa
     */
    public static boolean aceptable(UUID conclusion, EstandarPrueba estandar, List<Argumento> argumentos,
                                    Map<UUID, EstadoPremisa> premisas, Parametros p) {
        List<Argumento> pro = argumentos.stream()
                .filter(a -> a.conclusionId().equals(conclusion) && a.sentido() == Argumento.Sentido.PRO)
                .filter(a -> aplicable(a, premisas, estandar) && !esCiclico(a, argumentos))
                .toList();
        List<Argumento> contra = argumentos.stream()
                .filter(a -> a.conclusionId().equals(conclusion) && a.sentido() == Argumento.Sentido.CONTRA)
                .filter(a -> aplicable(a, premisas, estandar) && !esCiclico(a, argumentos))
                .toList();
        int pesoPro = pro.stream().mapToInt(Argumento::peso).sum();
        int pesoContra = contra.stream().mapToInt(Argumento::peso).sum();
        int mayorPro = pro.stream().mapToInt(Argumento::peso).max().orElse(0);
        int mayorContra = contra.stream().mapToInt(Argumento::peso).max().orElse(0);

        return switch (estandar) {
            case ESCRUTINIO -> !pro.isEmpty();
            case PREPONDERANCIA -> !pro.isEmpty() && pesoPro > pesoContra;
            case CLARO_Y_CONVINCENTE -> !pro.isEmpty() && pesoPro > pesoContra
                    && mayorPro >= p.umbralMayorProClaroYConvincente();
            case MAS_ALLA_DE_DUDA_RAZONABLE -> !pro.isEmpty() && pesoPro > pesoContra
                    && mayorPro >= p.umbralMayorProClaroYConvincente()
                    && mayorContra <= p.umbralContraMaximoMasAllaDeDuda()
                    && pro.stream().noneMatch(a -> a.premisas().stream().anyMatch(Argumento.Premisa::asumible));
        };
    }

    static Juicio juzgar(Argumento.Premisa premisa, Map<UUID, EstadoPremisa> premisas) {
        EstadoPremisa estado = premisas.getOrDefault(premisa.afirmacionId(), EstadoPremisa.de(EstadoAfirmacion.SIN_VERIFICAR));
        if (estado.estado() == EstadoAfirmacion.DISPUTADA) {
            return Juicio.CUESTIONADA;
        }
        if (estado.estado() == EstadoAfirmacion.VERIFICADA) {
            return Juicio.ACEPTADA;
        }
        if (premisa.asumible()) {
            return estado.conObjecion() ? Juicio.CUESTIONADA : Juicio.ACEPTADA;
        }
        if (estado.estado() == EstadoAfirmacion.NO_VERIFICABLE && estado.adoptadaComoPremisaDeValor()) {
            return Juicio.ACEPTADA;
        }
        return Juicio.NO_ACEPTADA;
    }

    /** Aplicable si todas sus premisas están aceptadas; en escrutinio basta con que ninguna esté no aceptada. */
    static boolean aplicable(Argumento a, Map<UUID, EstadoPremisa> premisas, EstandarPrueba estandar) {
        for (Argumento.Premisa premisa : a.premisas()) {
            Juicio juicio = juzgar(premisa, premisas);
            if (juicio == Juicio.NO_ACEPTADA) {
                return false;
            }
            if (juicio == Juicio.CUESTIONADA && estandar != EstandarPrueba.ESCRUTINIO) {
                return false;
            }
        }
        return true;
    }

    /** Ciclo: desde alguna premisa de a se llega, siguiendo "conclusión → premisas de sus argumentos", a la conclusión de a. */
    static boolean esCiclico(Argumento a, List<Argumento> todos) {
        Set<UUID> visitadas = new HashSet<>();
        Deque<UUID> porVisitar = new ArrayDeque<>();
        a.premisas().forEach(pr -> porVisitar.push(pr.afirmacionId()));
        while (!porVisitar.isEmpty()) {
            UUID actual = porVisitar.pop();
            if (actual.equals(a.conclusionId())) {
                return true;
            }
            if (!visitadas.add(actual)) {
                continue;
            }
            for (Argumento otro : todos) {
                if (otro.conclusionId().equals(actual)) {
                    otro.premisas().forEach(pr -> porVisitar.push(pr.afirmacionId()));
                }
            }
        }
        return false;
    }
}
