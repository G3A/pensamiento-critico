package pensamiento.tecnicas.f5;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
 * T29 · Pre-mortem (Klein 2007). Imagina que la decisión ya fracasó, prioriza las causas por probabilidad, exige una
 * mitigación por causa si la configuración lo pide y avisa de las categorías de ayuda sin causas. En el hito 4 no usa el
 * modelo: las causas las escribe la persona. Las reglas están en docs/ejemplos/T29.md.
 */
@Component
public class EjecutorPremortem implements Ejecutor<EjecutorPremortem.Config, EjecutorPremortem.Entrada, ResultadoPremortem> {

    public static final IdTecnica ID = IdTecnica.de("T29");
    public static final int VERSION_ESQUEMA = 1;
    static final int TOPE_CAUSAS = 10;

    public enum Probabilidad {
        ALTA, MEDIA, BAJA;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** Las categorías de ayuda, en orden, con su nombre corto. */
    public enum Categoria {
        PERSONAS("personas"), DINERO("dinero"), TIEMPO("tiempo"), TERCEROS("terceros"), CLIENTES("clientes"), REGLAS("reglas"), SALUD("salud");

        private final String corto;

        Categoria(String corto) {
            this.corto = corto;
        }

        public String corto() {
            return corto;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** Configuración de T29, versión de esquema 1. */
    public record Config(int causasMinimas, boolean exigirMitigacion, List<Categoria> categorias) {
        public Config {
            categorias = categorias == null ? List.of() : List.copyOf(categorias);
        }
    }

    public record Causa(String texto, Probabilidad probabilidad, Categoria categoria, String mitigacion) {
    }

    /** @param fecha la fecha en que se imagina el fracaso, AAAA-MM-DD; opcional */
    public record Entrada(String decision, String fecha, List<Causa> causas) {
        public Entrada {
            causas = causas == null ? List.of() : List.copyOf(causas);
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
    public Tipos<Config, Entrada, ResultadoPremortem> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoPremortem.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.causasMinimas() < 1 || config.causasMinimas() > TOPE_CAUSAS) {
            errores.add(new Validacion.Error("config.causasMinimas", "Las causas mínimas van de 1 a " + TOPE_CAUSAS + "."));
        }
        if (Textos.vacio(entrada.decision())) {
            errores.add(new Validacion.Error("decision", "Escribe la decisión que imaginas fracasada."));
        }
        if (!Textos.vacio(entrada.fecha())) {
            try {
                LocalDate.parse(entrada.fecha().strip());
            } catch (DateTimeParseException e) {
                errores.add(new Validacion.Error("fecha", "La fecha va como AAAA-MM-DD."));
            }
        }
        if (entrada.causas().isEmpty()) {
            errores.add(new Validacion.Error("causas", "Escribe al menos una causa del fracaso."));
        } else if (entrada.causas().size() > TOPE_CAUSAS) {
            errores.add(new Validacion.Error("causas", "Caben como máximo " + TOPE_CAUSAS + " causas."));
        }
        for (int i = 0; i < entrada.causas().size(); i++) {
            Causa c = entrada.causas().get(i);
            if (Textos.vacio(c.texto())) {
                errores.add(new Validacion.Error("causas[" + i + "].texto", "Escribe la causa C" + (i + 1) + "."));
            }
            if (c.probabilidad() == null) {
                errores.add(new Validacion.Error("causas[" + i + "].probabilidad", "Elige qué tan probable es la causa C" + (i + 1) + "."));
            }
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoPremortem> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        String decision = entrada.decision().strip();
        String clausula = Textos.comoClausula(decision);
        String enunciado = Textos.vacio(entrada.fecha()) ? "Ya pasó el tiempo y " + clausula + " fracasó. ¿Por qué?"
                : "Es " + Textos.fecha(LocalDate.parse(entrada.fecha().strip())) + " y " + clausula + " fracasó. ¿Por qué?";
        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        afirmaciones.add(new AfirmacionConRol(ctx.nuevoId().get(), decision, TipoAfirmacion.JUICIO_DE_VALOR, RolAfirmacion.OPCION,
                SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
        record Numerada(int indice, UUID id, Causa causa) {
        }
        List<Numerada> numeradas = new ArrayList<>();
        for (int i = 0; i < entrada.causas().size(); i++) {
            Causa c = entrada.causas().get(i);
            UUID id = ctx.nuevoId().get();
            numeradas.add(new Numerada(i, id, c));
            afirmaciones.add(new AfirmacionConRol(id, c.texto().strip(), TipoAfirmacion.CAUSAL, RolAfirmacion.HIPOTESIS, SentidoAfirmacion.PRODUCIDA,
                    OrigenAfirmacion.USUARIO));
        }
        List<ResultadoPremortem.CausaPriorizada> causas = new ArrayList<>();
        List<Pendiente> pendientes = new ArrayList<>();
        int conMitigacion = 0;
        for (Numerada n : numeradas.stream().sorted(Comparator.comparingInt((Numerada x) -> x.causa().probabilidad().ordinal())
                .thenComparingInt(Numerada::indice)).toList()) {
            String mitigacion = Textos.vacio(n.causa().mitigacion()) ? null : n.causa().mitigacion().strip();
            ResultadoPremortem.EstadoMitigacion estado = mitigacion != null ? ResultadoPremortem.EstadoMitigacion.CON_MITIGACION
                    : config.exigirMitigacion() ? ResultadoPremortem.EstadoMitigacion.FALTA_MITIGACION : ResultadoPremortem.EstadoMitigacion.SIN_MITIGACION;
            if (mitigacion != null) {
                conMitigacion++;
            }
            causas.add(new ResultadoPremortem.CausaPriorizada(n.id(), n.causa().texto().strip(), n.causa().probabilidad().toString(),
                    n.causa().categoria() == null ? null : n.causa().categoria().corto(), mitigacion, estado));
        }
        for (Numerada n : numeradas) {
            if (config.exigirMitigacion() && Textos.vacio(n.causa().mitigacion())) {
                pendientes.add(new Pendiente(TipoPendiente.REVISION, Optional.of(n.id()), Optional.empty(),
                        "Escribir una mitigación para: " + n.causa().texto().strip()));
            }
        }
        List<String> avisos = new ArrayList<>();
        int faltan = config.causasMinimas() - entrada.causas().size();
        if (faltan > 0) {
            avisos.add((faltan == 1 ? "Falta " : "Faltan ") + Textos.contar(faltan, "causa", "causas") + ": la configuración pide al menos "
                    + config.causasMinimas() + ".");
        }
        List<String> sinCausa = new ArrayList<>();
        for (Categoria cat : Categoria.values()) {
            if (config.categorias().contains(cat) && entrada.causas().stream().noneMatch(c -> c.categoria() == cat)) {
                sinCausa.add(cat.corto());
            }
        }
        if (!sinCausa.isEmpty()) {
            avisos.add("Ninguna causa de: " + String.join(", ", sinCausa) + ". ¿Seguro que por ahí no falla?");
        }
        String resumen = Textos.contar(causas.size(), "causa", "causas") + " · " + conMitigacion + " con mitigación.";
        ResultadoPremortem valor = new ResultadoPremortem(decision, enunciado, causas, avisos, resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen);
    }

    @Override
    public ResultadoPremortem migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }
}
