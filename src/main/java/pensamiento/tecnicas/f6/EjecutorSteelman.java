package pensamiento.tecnicas.f6;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.ConModelo;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.Propuesta;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.nucleo.Validacion;
import pensamiento.nucleo.puertos.RespuestaChat;
import pensamiento.tecnicas.comun.ModeloLocal;
import pensamiento.tecnicas.comun.Prompts;
import pensamiento.tecnicas.comun.Textos;

/**
 * T34 · Steelmanning (Rapoport, en Dennett 2013). La persona escribe la versión más fuerte de la postura contraria
 * o adopta la que propone el modelo; la tarjeta dice qué pregunta quedó sin responder y nunca que el steelman sea
 * correcto (corrección 13). Las reglas están en docs/ejemplos/T34.md.
 */
@Component
public class EjecutorSteelman implements Ejecutor<EjecutorSteelman.Config, EjecutorSteelman.Entrada, ResultadoSteelman>,
        ConModelo<EjecutorSteelman.Config, EjecutorSteelman.Entrada> {

    public static final IdTecnica ID = IdTecnica.de("T34");
    public static final int VERSION_ESQUEMA = 1;
    public static final String PROMPT = "t34-steelman";
    public static final int VERSION_PROMPT = 1;

    /** Marcas de caricatura, en minúscula y sin tildes, en este orden. */
    public static final List<String> CARICATURAS = List.of("no les importa", "no le importa", "solo quiere", "solo quieren",
            "solo les interesa", "son unos", "no entienden", "no saben nada", "egoista", "tienen algo que esconder");

    public static final String FALTA_STEELMAN = "Falta el steelman: ¿cuál es la mejor razón que tendría quien piensa así?";
    public static final String FALTA_CITA = "Falta la cita: ¿con qué palabras lo dice quien sostiene esa postura?";
    public static final String SIEMPRE = "¿Quien sostiene esa postura la reconocería como suya? Eso solo lo puedes responder tú, "
            + "preguntándole o comparando con su cita.";

    public enum Modo {
        MANUAL, MANUAL_Y_MODELO;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** Configuración de T34, versión de esquema 1. */
    public record Config(int longitudMaxima, Modo modo, boolean exigirCita) {
    }

    public record Razon(String texto) {
    }

    /**
     * @param origenSteelman "modelo" si el steelman vino de una propuesta adoptada; vacío si lo escribió la persona
     */
    public record Entrada(String posturaOriginal, String cita, String steelman, String origenSteelman, List<Razon> razones,
                          List<Propuesta> propuestas) {
        public Entrada {
            razones = razones == null ? List.of() : List.copyOf(razones);
            propuestas = propuestas == null ? List.of() : List.copyOf(propuestas);
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
    public Tipos<Config, Entrada, ResultadoSteelman> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoSteelman.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.longitudMaxima() < 20 || config.longitudMaxima() > 200) {
            errores.add(new Validacion.Error("config.longitudMaxima", "La longitud máxima va de 20 a 200 palabras."));
        }
        if (config.modo() == null) {
            errores.add(new Validacion.Error("config.modo", "Elige el modo."));
        }
        if (Textos.vacio(entrada.posturaOriginal())) {
            errores.add(new Validacion.Error("posturaOriginal", "Escribe la postura contraria como la oíste o la escribiste."));
        }
        for (Propuesta p : entrada.propuestas()) {
            if (!Propuesta.codigoValido(p.codigo()) || !"steelman".equals(p.destino())) {
                errores.add(new Validacion.Error("propuestas", "Hay una propuesta del modelo que no corresponde a esta técnica."));
                break;
            }
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoSteelman> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        String steelman = Textos.vacio(entrada.steelman()) ? null : entrada.steelman().strip();
        boolean delModelo = steelman != null && "modelo".equals(entrada.origenSteelman());
        int palabras = Textos.palabras(steelman);
        List<String> preguntas = new ArrayList<>();
        if (steelman == null) {
            preguntas.add(FALTA_STEELMAN);
        } else {
            if (palabras > config.longitudMaxima()) {
                preguntas.add("Tiene " + palabras + " palabras y el máximo es " + config.longitudMaxima() + ": ¿qué sobra?");
            }
            Textos.primeraFrase(steelman, CARICATURAS).ifPresent(marca ->
                    preguntas.add("Posible caricatura: «" + marca + "». ¿Lo diría así quien sostiene esa postura?"));
        }
        if (config.exigirCita() && Textos.vacio(entrada.cita())) {
            preguntas.add(FALTA_CITA);
        }
        ResultadoSteelman.Estado estado = preguntas.isEmpty() ? ResultadoSteelman.Estado.POR_CONFIRMAR : ResultadoSteelman.Estado.INCOMPLETO;
        preguntas.add(SIEMPRE);
        List<String> razones = entrada.razones().stream().map(Razon::texto).filter(t -> !Textos.vacio(t)).map(String::strip).toList();

        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        List<Pendiente> pendientes = new ArrayList<>();
        UUID afirmacionId = null;
        if (steelman != null) {
            afirmacionId = ctx.nuevoId().get();
            afirmaciones.add(new AfirmacionConRol(afirmacionId, steelman, TipoAfirmacion.JUICIO_DE_VALOR, RolAfirmacion.POSTURA,
                    SentidoAfirmacion.PRODUCIDA, delModelo ? OrigenAfirmacion.MODELO : OrigenAfirmacion.USUARIO, true));
            pendientes.add(new Pendiente(TipoPendiente.OBJECION, Optional.of(afirmacionId), Optional.empty(), "Responder al steelman: " + steelman));
        }
        for (Propuesta p : entrada.propuestas()) {
            if (!p.adoptada()) {
                afirmaciones.add(new AfirmacionConRol(ctx.nuevoId().get(), p.valor(), TipoAfirmacion.JUICIO_DE_VALOR, RolAfirmacion.POSTURA,
                        SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.MODELO, false));
            }
        }
        String resumen = steelman == null ? "Falta el steelman."
                : "Steelman de " + Textos.contar(palabras, "palabra", "palabras") + " · "
                + Textos.contar(razones.size(), "razón añadida", "razones añadidas") + " · " + estado.texto() + ".";
        ResultadoSteelman valor = new ResultadoSteelman(entrada.posturaOriginal().strip(), Textos.vacio(entrada.cita()) ? null : entrada.cita().strip(),
                steelman, steelman == null ? null : (delModelo ? "modelo" : "usuario"), palabras, config.longitudMaxima(), razones, preguntas,
                estado, entrada.propuestas(), resumen, afirmacionId);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen, List.of(),
                Resultado.registroDe(entrada.propuestas()), Optional.empty());
    }

    @Override
    public ResultadoSteelman migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }

    // ---------------------------------------------------------------------------------------------
    // El modelo propone (RF-14)
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean usaModelo(Config config) {
        return config.modo() == Modo.MANUAL_Y_MODELO;
    }

    @Override
    public List<Propuesta> propuestas(Entrada entrada) {
        return entrada.propuestas();
    }

    /** Una sola propuesta: el steelman redactado con streaming, validado y con hasta dos reintentos. */
    @Override
    public Propuestas proponer(Config config, Entrada entrada, Contexto ctx, Consumer<String> provisional, int primerNumero) {
        if (Textos.vacio(entrada.posturaOriginal())) {
            return Propuestas.cayo("Escribe primero la postura contraria: el modelo parte de ella.");
        }
        Prompts.Prompt prompt = Prompts.de(PROMPT, VERSION_PROMPT);
        Map<String, String> datos = Map.of("maximo", String.valueOf(config.longitudMaxima()), "postura", entrada.posturaOriginal().strip(),
                "cita", Textos.vacio(entrada.cita()) ? "(sin cita)" : "«" + entrada.cita().strip() + "»");
        return ModeloLocal.conCaida(ctx, ia -> {
            RespuestaChat r = ModeloLocal.redactar(ia, prompt.sistema(datos), prompt.pedido(datos),
                    ModeloLocal.textoCorto(config.longitudMaxima()).and(t -> Textos.primeraFrase(t, CARICATURAS).isEmpty()), provisional);
            return List.of(new Propuesta(Propuesta.codigo(primerNumero), "steelman", "Steelman propuesto", ModeloLocal.limpiar(r.texto()), "",
                    false, r.modelo(), r.digest(), prompt.version()));
        });
    }

    /** Adoptar pone la propuesta como steelman vigente, con origen modelo; las razones siguen siendo de la persona. */
    @Override
    public Entrada adoptar(Entrada entrada, String codigo) {
        List<Propuesta> propuestas = Propuesta.adoptar(entrada.propuestas(), codigo);
        Propuesta p = Propuesta.buscar(propuestas, codigo);
        return new Entrada(entrada.posturaOriginal(), entrada.cita(), p.valor(), "modelo", entrada.razones(), propuestas);
    }
}
