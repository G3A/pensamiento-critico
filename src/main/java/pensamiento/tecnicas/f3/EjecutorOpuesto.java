package pensamiento.tecnicas.f3;

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
 * T15 · Considera lo opuesto (Lord, Lepper y Preston 1984). La persona escribe una o más posturas opuestas a la suya y
 * qué cambiaría si fueran ciertas, y declara cómo se movió su confianza. El modelo puede redactar una postura opuesta;
 * no cuenta hasta que se adopta. Las reglas están en docs/ejemplos/T15.md.
 */
@Component
public class EjecutorOpuesto implements Ejecutor<EjecutorOpuesto.Config, EjecutorOpuesto.Entrada, ResultadoOpuesto>,
        ConModelo<EjecutorOpuesto.Config, EjecutorOpuesto.Entrada> {

    public static final IdTecnica ID = IdTecnica.de("T15");
    public static final int VERSION_ESQUEMA = 1;
    public static final String PROMPT = "t15-opuesta";
    public static final int VERSION_PROMPT = 1;
    public static final int PALABRAS_PROPUESTA = 40;
    public static final String FALTA_QUE_CAMBIARIA = "¿Qué cambiaría en tu decisión si esto fuera cierto?";

    public enum Modo {
        MANUAL, MANUAL_Y_MODELO;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** Configuración de T15, versión de esquema 1. */
    public record Config(int numeroOpuestas, Modo modo) {
    }

    /** @param origen "modelo" si vino de una propuesta adoptada; vacío si la escribió la persona */
    public record Opuesta(String texto, String queCambiaria, String origen) {
    }

    public record Entrada(String postura, Integer confianzaAntes, List<Opuesta> opuestas, Integer confianzaDespues, List<Propuesta> propuestas) {
        public Entrada {
            opuestas = opuestas == null ? List.of() : List.copyOf(opuestas);
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
    public Tipos<Config, Entrada, ResultadoOpuesto> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoOpuesto.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.numeroOpuestas() < 1 || config.numeroOpuestas() > 3) {
            errores.add(new Validacion.Error("config.numeroOpuestas", "El número de posturas opuestas va de 1 a 3."));
        }
        if (config.modo() == null) {
            errores.add(new Validacion.Error("config.modo", "Elige el modo."));
        }
        if (Textos.vacio(entrada.postura())) {
            errores.add(new Validacion.Error("postura", "Escribe tu postura."));
        }
        if (entrada.confianzaAntes() == null || entrada.confianzaAntes() < 0 || entrada.confianzaAntes() > 100) {
            errores.add(new Validacion.Error("confianzaAntes", "Escribe tu confianza antes, de 0 a 100."));
        }
        if (entrada.confianzaDespues() != null && (entrada.confianzaDespues() < 0 || entrada.confianzaDespues() > 100)) {
            errores.add(new Validacion.Error("confianzaDespues", "La confianza va de 0 a 100."));
        }
        if (entrada.propuestas().stream().anyMatch(p -> !Propuesta.codigoValido(p.codigo()) || !"opuestas".equals(p.destino()))) {
            errores.add(new Validacion.Error("propuestas", "Hay una propuesta del modelo que no corresponde a esta técnica."));
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoOpuesto> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        String postura = entrada.postura().strip();
        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        UUID posturaId = ctx.nuevoId().get();
        afirmaciones.add(new AfirmacionConRol(posturaId, postura, TipoAfirmacion.HECHO, RolAfirmacion.POSTURA, SentidoAfirmacion.PRODUCIDA,
                OrigenAfirmacion.USUARIO));
        List<ResultadoOpuesto.OpuestaEvaluada> opuestas = new ArrayList<>();
        for (Opuesta o : entrada.opuestas()) {
            if (o == null || Textos.vacio(o.texto())) {
                continue;
            }
            boolean delModelo = "modelo".equals(o.origen());
            String queCambiaria = Textos.vacio(o.queCambiaria()) ? null : o.queCambiaria().strip();
            opuestas.add(new ResultadoOpuesto.OpuestaEvaluada(o.texto().strip(), queCambiaria, queCambiaria == null ? FALTA_QUE_CAMBIARIA : null, delModelo));
            afirmaciones.add(new AfirmacionConRol(ctx.nuevoId().get(), o.texto().strip(), TipoAfirmacion.HECHO, RolAfirmacion.HIPOTESIS,
                    SentidoAfirmacion.PRODUCIDA, delModelo ? OrigenAfirmacion.MODELO : OrigenAfirmacion.USUARIO, true));
        }
        for (Propuesta p : entrada.propuestas()) {
            if (!p.adoptada()) {
                afirmaciones.add(new AfirmacionConRol(ctx.nuevoId().get(), p.valor(), TipoAfirmacion.HECHO, RolAfirmacion.HIPOTESIS,
                        SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.MODELO, false));
            }
        }
        int faltan = config.numeroOpuestas() - opuestas.size();
        String faltantes = faltan > 0 ? "Falta " + Textos.contar(faltan, "postura opuesta", "posturas opuestas")
                + ": ¿qué diría alguien que piensa lo contrario?" : null;
        int antes = entrada.confianzaAntes();
        String confianza;
        String confianzaResumen;
        List<Pendiente> pendientes = new ArrayList<>();
        if (entrada.confianzaDespues() == null) {
            confianza = "confianza " + antes + "% antes; falta la de después";
            confianzaResumen = "confianza " + antes + "% antes";
            pendientes.add(new Pendiente(TipoPendiente.REVISION, Optional.of(posturaId), Optional.empty(), "Volver a estimar tu confianza en: " + postura));
        } else {
            int despues = entrada.confianzaDespues();
            String flecha = "confianza " + antes + "% → " + despues + "%";
            confianza = despues < antes ? flecha + " (bajó " + (antes - despues) + " puntos)"
                    : despues > antes ? flecha + " (subió " + (despues - antes) + " puntos)"
                    : flecha + ": no se movió. Si lo opuesto te parece imposible, escribe por qué.";
            confianzaResumen = flecha;
        }
        String resumen = Textos.contar(opuestas.size(), "postura opuesta", "posturas opuestas") + " de " + config.numeroOpuestas() + " · "
                + confianzaResumen + ".";
        ResultadoOpuesto valor = new ResultadoOpuesto(postura, opuestas, config.numeroOpuestas(), faltantes, confianza, entrada.propuestas(), resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen, List.of(), Resultado.registroDe(entrada.propuestas()),
                Optional.empty());
    }

    @Override
    public ResultadoOpuesto migrar(Json datosViejos, int desdeVersion) {
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

    /** Una postura opuesta redactada por el modelo, solo si faltan; con streaming, validada y hasta dos reintentos. */
    @Override
    public Propuestas proponer(Config config, Entrada entrada, Contexto ctx, Consumer<String> provisional, int primerNumero) {
        if (Textos.vacio(entrada.postura())) {
            return Propuestas.cayo("Escribe primero tu postura: el modelo parte de ella.");
        }
        List<String> escritas = entrada.opuestas().stream().filter(o -> o != null && !Textos.vacio(o.texto())).map(o -> "«" + o.texto().strip() + "»").toList();
        if (escritas.size() >= config.numeroOpuestas()) {
            return Propuestas.de(List.of());
        }
        Prompts.Prompt prompt = Prompts.de(PROMPT, VERSION_PROMPT);
        Map<String, String> datos = Map.of("postura", entrada.postura().strip(), "opuestas", escritas.isEmpty() ? "(ninguna)" : String.join(", ", escritas));
        return ModeloLocal.conCaida(ctx, ia -> {
            RespuestaChat r = ModeloLocal.redactar(ia, prompt.sistema(datos), prompt.pedido(datos), ModeloLocal.textoCorto(PALABRAS_PROPUESTA), provisional);
            return List.of(new Propuesta(Propuesta.codigo(primerNumero), "opuestas", "Postura opuesta propuesta", ModeloLocal.limpiar(r.texto()), "",
                    false, r.modelo(), r.digest(), prompt.version()));
        });
    }

    /** Adoptar agrega la propuesta como postura opuesta, con origen modelo; "qué cambiaría" lo escribe la persona. */
    @Override
    public Entrada adoptar(Entrada entrada, String codigo) {
        List<Propuesta> propuestas = Propuesta.adoptar(entrada.propuestas(), codigo);
        Propuesta p = Propuesta.buscar(propuestas, codigo);
        List<Opuesta> opuestas = new ArrayList<>(entrada.opuestas().stream().filter(o -> o != null && !Textos.vacio(o.texto())).toList());
        opuestas.add(new Opuesta(p.valor(), null, "modelo"));
        return new Entrada(entrada.postura(), entrada.confianzaAntes(), opuestas, entrada.confianzaDespues(), propuestas);
    }
}
