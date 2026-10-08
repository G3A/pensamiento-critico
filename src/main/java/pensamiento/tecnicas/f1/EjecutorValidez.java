package pensamiento.tecnicas.f1;

import java.util.ArrayList;
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
import pensamiento.tecnicas.f1.ResultadoValidez.Tipo;

/**
 * T05 · Validez y solidez (lógica clásica; Copi 1953). La app no decide si la conclusión se sigue: se lo pregunta a la
 * persona y muestra qué cambia con la respuesta; la solidez solo se mira si la forma se sostiene. No usa IA. Las
 * reglas están en docs/ejemplos/T05.md.
 */
@Component
public class EjecutorValidez implements Ejecutor<EjecutorValidez.Config, EjecutorValidez.Entrada, ResultadoValidez> {

    public static final IdTecnica ID = IdTecnica.de("T05");
    public static final int VERSION_ESQUEMA = 1;

    static final List<String> PROBABILIDAD = List.of("probablemente", "seguramente", "posiblemente", "es probable", "quizas", "tal vez", "casi seguro");
    static final List<String> UNIVERSALES = List.of("todo", "todos", "todas", "ningun", "ninguna", "nadie", "siempre", "nunca");

    public enum TipoEsperado {
        DEDUCTIVO, INDUCTIVO, DETECTAR;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public enum Estado {
        ESTABLECIDA, SIN_ESTABLECER;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public enum Seguimiento {
        SE_SIGUE, NO_SE_SIGUE, NO_LO_SE;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /**
     * Configuración de T05, versión de esquema 1.
     *
     * @param umbralSolidez cuántas premisas pueden quedar sin establecer en un argumento inductivo (en uno deductivo, ninguna)
     */
    public record Config(TipoEsperado tipoEsperado, int umbralSolidez) {
    }

    public record Premisa(String texto, Estado estado) {
    }

    public record Entrada(List<Premisa> premisas, String conclusion, Seguimiento seguimiento) {
        public Entrada {
            premisas = premisas == null ? List.of() : List.copyOf(premisas);
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
    public Tipos<Config, Entrada, ResultadoValidez> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoValidez.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.tipoEsperado() == null) {
            errores.add(new Validacion.Error("config.tipoEsperado", "Elige el tipo esperado."));
        }
        if (config.umbralSolidez() < 0 || config.umbralSolidez() > 3) {
            errores.add(new Validacion.Error("config.umbralSolidez", "La tolerancia va de 0 a 3 premisas."));
        }
        List<Premisa> conTexto = entrada.premisas().stream().filter(p -> p != null && !Textos.vacio(p.texto())).toList();
        if (conTexto.isEmpty() || conTexto.size() > 4) {
            errores.add(new Validacion.Error("premisas", "Escribe de una a cuatro premisas."));
        }
        if (conTexto.stream().anyMatch(p -> p.estado() == null)) {
            errores.add(new Validacion.Error("premisas", "Marca si cada premisa está establecida o no."));
        }
        if (Textos.vacio(entrada.conclusion())) {
            errores.add(new Validacion.Error("conclusion", "Escribe la conclusión."));
        }
        if (entrada.seguimiento() == null) {
            errores.add(new Validacion.Error("seguimiento", "Responde si la conclusión se seguiría de las premisas."));
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoValidez> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        String conclusion = entrada.conclusion().strip();
        List<Premisa> premisas = entrada.premisas().stream().filter(p -> !Textos.vacio(p.texto())).toList();
        Tipo tipo = switch (config.tipoEsperado()) {
            case DEDUCTIVO -> Tipo.DEDUCTIVO;
            case INDUCTIVO -> Tipo.INDUCTIVO;
            case DETECTAR -> detectar(premisas, conclusion);
        };
        boolean deductivo = tipo == Tipo.DEDUCTIVO;
        String estadoForma;
        String fraseForma;
        switch (entrada.seguimiento()) {
            case SE_SIGUE -> {
                estadoForma = deductivo ? "se sigue" : "fuerte";
                fraseForma = deductivo ? "Si las premisas fueran ciertas, la conclusión se seguiría."
                        : "Si las premisas fueran ciertas, la conclusión sería probable.";
            }
            case NO_SE_SIGUE -> {
                estadoForma = deductivo ? "no se sigue" : "débil";
                fraseForma = deductivo ? "Aunque las premisas fueran ciertas, la conclusión podría ser falsa: hay un salto en la forma."
                        : "Aunque las premisas fueran ciertas, la conclusión seguiría siendo poco probable.";
            }
            default -> {
                estadoForma = "sin responder";
                fraseForma = deductivo ? "Falta responder: si las premisas fueran ciertas, ¿podría la conclusión ser falsa?"
                        : "Falta responder: si las premisas fueran ciertas, ¿qué tan probable sería la conclusión?";
            }
        }
        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        List<ResultadoValidez.PremisaEvaluada> evaluadas = new ArrayList<>();
        for (Premisa p : premisas) {
            UUID id = ctx.nuevoId().get();
            afirmaciones.add(new AfirmacionConRol(id, p.texto().strip(), TipoAfirmacion.HECHO, RolAfirmacion.PREMISA, SentidoAfirmacion.PRODUCIDA,
                    OrigenAfirmacion.USUARIO));
            evaluadas.add(new ResultadoValidez.PremisaEvaluada(p.texto().strip(), p.estado() == Estado.ESTABLECIDA, id));
        }
        afirmaciones.add(new AfirmacionConRol(ctx.nuevoId().get(), conclusion, TipoAfirmacion.HECHO, RolAfirmacion.CONCLUSION,
                SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));

        String estadoSolidez;
        String fraseSolidez;
        String solidezCorta;
        List<String> preguntas = new ArrayList<>();
        List<Pendiente> pendientes = new ArrayList<>();
        if (entrada.seguimiento() != Seguimiento.SE_SIGUE) {
            estadoSolidez = "no se evalúa";
            fraseSolidez = "Primero la forma: si la conclusión no se sigue, que las premisas sean ciertas no basta.";
            solidezCorta = "solidez no se evalúa";
        } else {
            List<ResultadoValidez.PremisaEvaluada> sinEstablecer = evaluadas.stream().filter(p -> !p.establecida()).toList();
            int tolerancia = deductivo ? 0 : config.umbralSolidez();
            if (sinEstablecer.size() <= tolerancia) {
                estadoSolidez = "establecida por ti";
                fraseSolidez = "Marcaste como establecidas las premisas que hacen falta. Si alguna deja de estarlo, vuelve a revisar.";
            } else {
                estadoSolidez = "sin establecer";
                sinEstablecer.forEach(p -> preguntas.add("Falta responder: ¿Es cierto que " + Textos.comoClausula(p.texto()) + "?"));
                fraseSolidez = String.join(" ", preguntas);
            }
            sinEstablecer.forEach(p -> pendientes.add(new Pendiente(TipoPendiente.VERIFICACION, Optional.of(p.afirmacionId()), Optional.empty(),
                    "Verificar la premisa: " + p.texto())));
            solidezCorta = sinEstablecer.isEmpty() ? "premisas establecidas"
                    : Textos.contar(sinEstablecer.size(), "premisa sin establecer", "premisas sin establecer");
        }
        String resumen = Textos.mayusculaInicial(tipo.texto()) + " · " + estadoForma + " · " + solidezCorta + ".";
        ResultadoValidez valor = new ResultadoValidez(tipo, config.tipoEsperado() == TipoEsperado.DETECTAR, conclusion, evaluadas, estadoForma,
                fraseForma, estadoSolidez, fraseSolidez, preguntas, resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen);
    }

    /** Inductivo si la conclusión tiene marca de probabilidad; si no, deductivo si alguna premisa es universal o empieza con "si". */
    static Tipo detectar(List<Premisa> premisas, String conclusion) {
        if (Textos.primeraFrase(conclusion, PROBABILIDAD).isPresent()) {
            return Tipo.INDUCTIVO;
        }
        for (Premisa p : premisas) {
            if (Textos.primeraFrase(p.texto(), UNIVERSALES).isPresent() || Textos.plegar(p.texto().strip()).startsWith("si ")) {
                return Tipo.DEDUCTIVO;
            }
        }
        return Tipo.INDUCTIVO;
    }

    @Override
    public ResultadoValidez migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }
}
