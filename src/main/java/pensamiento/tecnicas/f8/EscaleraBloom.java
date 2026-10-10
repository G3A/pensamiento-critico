package pensamiento.tecnicas.f8;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import pensamiento.nucleo.NivelBloom;

/**
 * La regla de T48 · Taxonomía de Bloom sobre los intentos de un tema, en orden: qué niveles quedan dominados, cuál está en
 * curso y cuáles bloqueados. Con avance automático cada nivel activo se abre al dominar el anterior y un intento cuenta solo
 * si su nivel está activo y abierto en ese momento; así un intento nunca abre dos niveles. Con avance manual todo nivel activo
 * está abierto. La usan el ejecutor de T48 y el Dojo. Reglas en docs/ejemplos/T48.md.
 */
public final class EscaleraBloom {

    public enum Estado {
        DOMINADO("dominado"), EN_CURSO("en curso"), BLOQUEADO("bloqueado"), APAGADO("apagado");

        private final String texto;

        Estado(String texto) {
            this.texto = texto;
        }

        public String texto() {
            return texto;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public record Parametros(List<NivelBloom> activos, boolean avanceAutomatico, int aciertosParaDominar) {
        public Parametros {
            activos = List.copyOf(activos);
            if (activos.isEmpty()) {
                throw new IllegalArgumentException("Hace falta al menos un nivel activo");
            }
            if (aciertosParaDominar < 1) {
                throw new IllegalArgumentException("Los aciertos para dominar son al menos 1");
            }
        }
    }

    public record Intento(NivelBloom nivel, boolean acierto) {
    }

    public record Nivel(NivelBloom nivel, Estado estado, int aciertos, int intentos) {
    }

    /**
     * @param actual       el primer nivel activo sin dominar; si todos están dominados, el último activo
     * @param fueraDeNivel intentos en un nivel activo todavía bloqueado, que no cuentan
     * @param apagados     intentos en niveles no activos, que no cuentan
     */
    public record Progreso(List<Nivel> niveles, NivelBloom actual, boolean todosDominados, int fueraDeNivel, int apagados) {
        public Progreso {
            niveles = List.copyOf(niveles);
        }

        public Nivel de(NivelBloom nivel) {
            return niveles.stream().filter(n -> n.nivel() == nivel).findFirst().orElseThrow();
        }

        public int dominados() {
            return (int) niveles.stream().filter(n -> n.estado() == Estado.DOMINADO).count();
        }

        /** El primer nivel activo bloqueado; vacío si no hay. */
        public java.util.Optional<NivelBloom> siguienteBloqueado() {
            return niveles.stream().filter(n -> n.estado() == Estado.BLOQUEADO).map(Nivel::nivel).findFirst();
        }
    }

    private EscaleraBloom() {
    }

    public static Progreso calcular(Parametros p, List<Intento> intentos) {
        List<NivelBloom> activos = java.util.Arrays.stream(NivelBloom.values()).filter(p.activos()::contains).toList();
        Map<NivelBloom, Integer> aciertos = new EnumMap<>(NivelBloom.class);
        Map<NivelBloom, Integer> hechos = new EnumMap<>(NivelBloom.class);
        int fuera = 0;
        int apagados = 0;
        for (Intento i : intentos) {
            if (!activos.contains(i.nivel())) {
                apagados++;
            } else if (!abierto(i.nivel(), activos, aciertos, p)) {
                fuera++;
            } else {
                hechos.merge(i.nivel(), 1, Integer::sum);
                if (i.acierto()) {
                    aciertos.merge(i.nivel(), 1, Integer::sum);
                }
            }
        }
        List<Nivel> niveles = new ArrayList<>();
        for (NivelBloom n : NivelBloom.values()) {
            int a = aciertos.getOrDefault(n, 0);
            Estado estado;
            if (!activos.contains(n)) {
                estado = Estado.APAGADO;
            } else if (a >= p.aciertosParaDominar()) {
                estado = Estado.DOMINADO;
            } else if (abierto(n, activos, aciertos, p)) {
                estado = Estado.EN_CURSO;
            } else {
                estado = Estado.BLOQUEADO;
            }
            niveles.add(new Nivel(n, estado, a, hechos.getOrDefault(n, 0)));
        }
        NivelBloom actual = activos.stream().filter(n -> aciertos.getOrDefault(n, 0) < p.aciertosParaDominar()).findFirst()
                .orElse(activos.getLast());
        boolean todos = activos.stream().allMatch(n -> aciertos.getOrDefault(n, 0) >= p.aciertosParaDominar());
        return new Progreso(niveles, actual, todos, fuera, apagados);
    }

    /** Con avance manual, todo nivel activo; con automático, el primero y los que siguen a un nivel activo dominado. */
    private static boolean abierto(NivelBloom nivel, List<NivelBloom> activos, Map<NivelBloom, Integer> aciertos, Parametros p) {
        int i = activos.indexOf(nivel);
        return !p.avanceAutomatico() || i == 0 || aciertos.getOrDefault(activos.get(i - 1), 0) >= p.aciertosParaDominar();
    }
}
