package pensamiento.tecnicas.f6;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.nucleo.Validacion;
import pensamiento.tecnicas.comun.Textos;

/**
 * T39 · Razonamiento ético: consecuencias, deberes y virtudes (Rest 1986; marcos clásicos). Cruza los marcos activos con
 * las partes afectadas, pide una objeción por marco y señala los conflictos por reglas: una parte que un marco favorece y
 * otro perjudica, y un reparto desigual entre partes. En este hito no usa el modelo. Reglas en docs/ejemplos/T39.md.
 */
@Component
public class EjecutorEtico implements Ejecutor<EjecutorEtico.Config, EjecutorEtico.Entrada, ResultadoEtico> {

    public static final IdTecnica ID = IdTecnica.de("T39");
    public static final int VERSION_ESQUEMA = 1;

    public enum Marco {
        CONSECUENCIAS("consecuencias"), DEBERES("deberes"), VIRTUDES("virtudes");

        private final String nombre;

        Marco(String nombre) {
            this.nombre = nombre;
        }

        public String nombre() {
            return nombre;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** +1, 0 o −1 en el formulario. */
    public enum Valoracion {
        MAS(1), CERO(0), MENOS(-1);

        private final int valor;

        Valoracion(int valor) {
            this.valor = valor;
        }

        public int valor() {
            return valor;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** Configuración de T39, versión de esquema 1. */
    public record Config(List<Marco> marcos, int partesMinimas, boolean exigirObjecion) {
        public Config {
            marcos = marcos == null ? List.of() : List.copyOf(marcos);
        }
    }

    /** Una valoración vacía cuenta como 0. */
    public record Parte(String parte, Valoracion consecuencias, Valoracion deberes, Valoracion virtudes) {
        int valor(Marco m) {
            Valoracion v = switch (m) {
                case CONSECUENCIAS -> consecuencias;
                case DEBERES -> deberes;
                case VIRTUDES -> virtudes;
            };
            return v == null ? 0 : v.valor();
        }
    }

    public record Entrada(String decision, List<Parte> partes, String objecionConsecuencias, String objecionDeberes, String objecionVirtudes) {
        public Entrada {
            partes = partes == null ? List.of() : List.copyOf(partes);
        }

        String objecion(Marco m) {
            return switch (m) {
                case CONSECUENCIAS -> objecionConsecuencias;
                case DEBERES -> objecionDeberes;
                case VIRTUDES -> objecionVirtudes;
            };
        }
    }

    @Override
    public IdTecnica id() {
        return ID;
    }

    @Override
    public int versionEsquema() {
        return VERSION_ESQUEMA;
    }

    @Override
    public Tipos<Config, Entrada, ResultadoEtico> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoEtico.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.marcos().isEmpty()) {
            errores.add(new Validacion.Error("config.marcos", "Activa al menos un marco."));
        }
        if (config.partesMinimas() < 1 || config.partesMinimas() > 6) {
            errores.add(new Validacion.Error("config.partesMinimas", "Las partes mínimas van de 1 a 6."));
        }
        if (Textos.vacio(entrada.decision())) {
            errores.add(new Validacion.Error("decision", "Escribe la decisión."));
        }
        if (entrada.partes().isEmpty() || entrada.partes().stream().anyMatch(p -> Textos.vacio(p.parte()))) {
            errores.add(new Validacion.Error("partes", "Escribe al menos una parte afectada, cada una con su nombre."));
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoEtico> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        List<Marco> marcos = List.of(Marco.values()).stream().filter(config.marcos()::contains).toList();
        List<String> partes = entrada.partes().stream().map(p -> p.parte().strip()).toList();
        List<ResultadoEtico.FilaMarco> filas = new ArrayList<>();
        for (Marco m : marcos) {
            List<Integer> valores = entrada.partes().stream().map(p -> p.valor(m)).toList();
            filas.add(new ResultadoEtico.FilaMarco(m.toString(), m.nombre(), valores, valores.stream().mapToInt(Integer::intValue).sum()));
        }
        List<String> conflictos = new ArrayList<>();
        for (Parte p : entrada.partes()) {
            Optional<Marco> mas = marcos.stream().filter(m -> p.valor(m) == 1).findFirst();
            Optional<Marco> menos = marcos.stream().filter(m -> p.valor(m) == -1).findFirst();
            if (mas.isPresent() && menos.isPresent()) {
                conflictos.add("Conflicto en «" + p.parte().strip() + "»: " + mas.get().nombre() + " da +1 y " + menos.get().nombre() + " da −1.");
            }
        }
        List<String> beneficiadas = new ArrayList<>();
        List<String> perjudicadas = new ArrayList<>();
        for (Parte p : entrada.partes()) {
            int total = marcos.stream().mapToInt(p::valor).sum();
            if (total > 0) {
                beneficiadas.add(p.parte().strip());
            } else if (total < 0) {
                perjudicadas.add(p.parte().strip());
            }
        }
        if (!beneficiadas.isEmpty() && !perjudicadas.isEmpty()) {
            conflictos.add("Reparto desigual: beneficia a " + Textos.enumerar(beneficiadas) + " y perjudica a " + Textos.enumerar(perjudicadas) + ".");
        }
        int nConflictos = conflictos.size();
        if (conflictos.isEmpty()) {
            conflictos.add("Sin conflictos entre marcos por las reglas: revisa tú si alguno pesa más que los otros.");
        }
        String decision = entrada.decision().strip();
        List<String> avisos = new ArrayList<>();
        List<Pendiente> pendientes = new ArrayList<>();
        if (partes.size() < config.partesMinimas()) {
            avisos.add("Hay " + Textos.contar(partes.size(), "parte afectada", "partes afectadas") + ": la configuración pide al menos "
                    + config.partesMinimas() + ". ¿A quién más afecta?");
        }
        List<ResultadoEtico.Objecion> objeciones = new ArrayList<>();
        for (Marco m : marcos) {
            String texto = Textos.vacio(entrada.objecion(m)) ? null : entrada.objecion(m).strip();
            objeciones.add(new ResultadoEtico.Objecion(m.toString(), m.nombre(), texto));
            if (texto == null && config.exigirObjecion()) {
                avisos.add("Falta la objeción del marco " + m.nombre() + ".");
                pendientes.add(new Pendiente(TipoPendiente.REVISION, Optional.empty(), Optional.empty(), "Escribir la objeción del marco " + m.nombre()));
            }
        }
        if (partes.size() < config.partesMinimas()) {
            pendientes.add(new Pendiente(TipoPendiente.REVISION, Optional.empty(), Optional.empty(), "Agregar partes afectadas a: " + decision));
        }
        String resumen = Textos.contar(partes.size(), "parte", "partes") + " × " + Textos.contar(marcos.size(), "marco", "marcos") + " · "
                + (nConflictos == 0 ? "sin conflictos." : Textos.contar(nConflictos, "conflicto", "conflictos") + ".");
        List<AfirmacionConRol> afirmaciones = List.of(new AfirmacionConRol(ctx.nuevoId().get(), decision, TipoAfirmacion.JUICIO_DE_VALOR, RolAfirmacion.OPCION,
                SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
        ResultadoEtico valor = new ResultadoEtico(decision, partes, filas, conflictos, objeciones, avisos, resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen);
    }

    @Override
    public ResultadoEtico migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA + "; se pidió migrar desde la " + desdeVersion);
    }
}
