package pensamiento.tecnicas.f1;

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
import pensamiento.tecnicas.f1.ResultadoAnalogia.Fuerza;

/**
 * T07 · Razonamiento por analogía (Walton 1996). La persona lista similitudes y diferencias, marca la diferencia clave y
 * si la verificó; la fuerza sale de reglas escritas. El modelo puede proponer una diferencia; no cuenta hasta que se
 * adopta y nunca se marca clave sola. Las reglas están en docs/ejemplos/T07.md.
 */
@Component
public class EjecutorAnalogia implements Ejecutor<EjecutorAnalogia.Config, EjecutorAnalogia.Entrada, ResultadoAnalogia>,
        ConModelo<EjecutorAnalogia.Config, EjecutorAnalogia.Entrada> {

    public static final IdTecnica ID = IdTecnica.de("T07");
    public static final int VERSION_ESQUEMA = 1;
    public static final String PROMPT = "t07-diferencia";
    public static final int VERSION_PROMPT = 1;
    public static final int PALABRAS_PROPUESTA = 25;

    public enum Modo {
        PLANTILLAS, PLANTILLAS_Y_MODELO;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** Configuración de T07, versión de esquema 1. */
    public record Config(int minimoSimilitudes, int minimoDiferencias, boolean exigirClaveVerificada, Modo modo) {
    }

    public record Similitud(String texto) {
    }

    /** @param origen "modelo" si vino de una propuesta adoptada */
    public record Diferencia(String texto, boolean clave, boolean verificada, String origen) {
    }

    public record Entrada(String caso, String conclusion, List<Similitud> similitudes, List<Diferencia> diferencias, List<Propuesta> propuestas) {
        public Entrada {
            similitudes = similitudes == null ? List.of() : List.copyOf(similitudes);
            diferencias = diferencias == null ? List.of() : List.copyOf(diferencias);
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
    public Tipos<Config, Entrada, ResultadoAnalogia> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoAnalogia.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.minimoSimilitudes() < 1 || config.minimoSimilitudes() > 5 || config.minimoDiferencias() < 1 || config.minimoDiferencias() > 5) {
            errores.add(new Validacion.Error("config.minimoSimilitudes", "Los mínimos van de 1 a 5."));
        }
        if (config.modo() == null) {
            errores.add(new Validacion.Error("config.modo", "Elige el modo."));
        }
        if (Textos.vacio(entrada.caso())) {
            errores.add(new Validacion.Error("caso", "Escribe el caso de origen: a quién le funcionó o no."));
        }
        if (Textos.vacio(entrada.conclusion())) {
            errores.add(new Validacion.Error("conclusion", "Escribe la conclusión que sacas para tu caso."));
        }
        if (entrada.propuestas().stream().anyMatch(p -> !Propuesta.codigoValido(p.codigo()) || !"diferencias".equals(p.destino()))) {
            errores.add(new Validacion.Error("propuestas", "Hay una propuesta del modelo que no corresponde a esta técnica."));
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoAnalogia> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        List<String> similitudes = entrada.similitudes().stream().filter(s -> s != null && !Textos.vacio(s.texto())).map(s -> s.texto().strip()).toList();
        List<ResultadoAnalogia.DiferenciaEvaluada> diferencias = entrada.diferencias().stream().filter(d -> d != null && !Textos.vacio(d.texto()))
                .map(d -> new ResultadoAnalogia.DiferenciaEvaluada(d.texto().strip(), d.clave(), d.verificada(), "modelo".equals(d.origen()))).toList();
        int s = similitudes.size();
        int d = diferencias.size();
        Fuerza fuerza;
        String motivo;
        Optional<ResultadoAnalogia.DiferenciaEvaluada> primeraClave = diferencias.stream().filter(ResultadoAnalogia.DiferenciaEvaluada::clave).findFirst();
        if (s < config.minimoSimilitudes() || d < config.minimoDiferencias()) {
            fuerza = Fuerza.INCOMPLETA;
            List<String> faltan = new ArrayList<>();
            if (s < config.minimoSimilitudes()) {
                faltan.add(Textos.contar(config.minimoSimilitudes() - s, "similitud", "similitudes"));
            }
            if (d < config.minimoDiferencias()) {
                faltan.add(Textos.contar(config.minimoDiferencias() - d, "diferencia", "diferencias"));
            }
            int total = (s < config.minimoSimilitudes() ? config.minimoSimilitudes() - s : 0) + (d < config.minimoDiferencias() ? config.minimoDiferencias() - d : 0);
            motivo = (total == 1 ? "Falta " : "Faltan ") + Textos.enumerar(faltan) + " para juzgar la analogía.";
        } else if (primeraClave.isPresent() && (primeraClave.get().verificada() || config.exigirClaveVerificada())) {
            fuerza = Fuerza.DEBIL;
            motivo = primeraClave.get().verificada() ? "La diferencia clave es real: " + primeraClave.get().texto()
                    : "La diferencia clave no está verificada: " + primeraClave.get().texto();
        } else if (primeraClave.isPresent()) {
            fuerza = Fuerza.MEDIA;
            motivo = "La diferencia clave «" + primeraClave.get().texto() + "» puede romper la analogía.";
        } else if (s > d) {
            fuerza = Fuerza.FUERTE;
            motivo = Textos.contar(s, "similitud", "similitudes") + " contra " + Textos.contar(d, "diferencia", "diferencias")
                    + " y ninguna clave. Revisa que las similitudes importen para la conclusión.";
        } else {
            fuerza = Fuerza.MEDIA;
            motivo = "Tantas diferencias como similitudes, o más: la analogía apenas sostiene la conclusión.";
        }
        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        afirmaciones.add(new AfirmacionConRol(ctx.nuevoId().get(), entrada.conclusion().strip(), TipoAfirmacion.HECHO, RolAfirmacion.CONCLUSION,
                SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
        List<Pendiente> pendientes = new ArrayList<>();
        for (ResultadoAnalogia.DiferenciaEvaluada x : diferencias) {
            UUID id = ctx.nuevoId().get();
            afirmaciones.add(new AfirmacionConRol(id, x.texto(), TipoAfirmacion.HECHO, RolAfirmacion.PREMISA, SentidoAfirmacion.PRODUCIDA,
                    x.delModelo() ? OrigenAfirmacion.MODELO : OrigenAfirmacion.USUARIO, true));
            if (x.clave() && !x.verificada()) {
                pendientes.add(new Pendiente(TipoPendiente.VERIFICACION, Optional.of(id), Optional.empty(), "Verificar la diferencia clave: " + x.texto()));
            }
        }
        for (Propuesta p : entrada.propuestas()) {
            if (!p.adoptada()) {
                afirmaciones.add(new AfirmacionConRol(ctx.nuevoId().get(), p.valor(), TipoAfirmacion.HECHO, RolAfirmacion.PREMISA,
                        SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.MODELO, false));
            }
        }
        String resumen = Textos.contar(s, "similitud", "similitudes") + " y " + Textos.contar(d, "diferencia", "diferencias") + " · fuerza "
                + fuerza.texto() + ".";
        ResultadoAnalogia valor = new ResultadoAnalogia(entrada.caso().strip(), entrada.conclusion().strip(), similitudes, diferencias, fuerza, motivo,
                entrada.propuestas(), resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen, List.of(), Resultado.registroDe(entrada.propuestas()),
                Optional.empty());
    }

    @Override
    public ResultadoAnalogia migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }

    // ---------------------------------------------------------------------------------------------
    // El modelo propone (RF-14)
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean usaModelo(Config config) {
        return config.modo() == Modo.PLANTILLAS_Y_MODELO;
    }

    @Override
    public List<Propuesta> propuestas(Entrada entrada) {
        return entrada.propuestas();
    }

    /** Una diferencia relevante que la persona no escribió, de 25 palabras o menos. */
    @Override
    public Propuestas proponer(Config config, Entrada entrada, Contexto ctx, Consumer<String> provisional, int primerNumero) {
        if (Textos.vacio(entrada.caso()) || Textos.vacio(entrada.conclusion())) {
            return Propuestas.cayo("Escribe primero el caso de origen y tu conclusión: el modelo parte de ellos.");
        }
        Prompts.Prompt prompt = Prompts.de(PROMPT, VERSION_PROMPT);
        Map<String, String> datos = Map.of("caso", entrada.caso().strip(), "conclusion", entrada.conclusion().strip(),
                "similitudes", lista(entrada.similitudes().stream().filter(x -> x != null).map(Similitud::texto).toList()),
                "diferencias", lista(entrada.diferencias().stream().filter(x -> x != null).map(Diferencia::texto).toList()));
        return ModeloLocal.conCaida(ctx, ia -> {
            RespuestaChat r = ModeloLocal.redactar(ia, prompt.sistema(datos), prompt.pedido(datos), ModeloLocal.textoCorto(PALABRAS_PROPUESTA), provisional);
            return List.of(new Propuesta(Propuesta.codigo(primerNumero), "diferencias", "Diferencia propuesta", ModeloLocal.limpiar(r.texto()), "",
                    false, r.modelo(), r.digest(), prompt.version()));
        });
    }

    /** Adoptar agrega la diferencia, no clave y sin verificar, con origen modelo. */
    @Override
    public Entrada adoptar(Entrada entrada, String codigo) {
        List<Propuesta> propuestas = Propuesta.adoptar(entrada.propuestas(), codigo);
        Propuesta p = Propuesta.buscar(propuestas, codigo);
        List<Diferencia> diferencias = new ArrayList<>(entrada.diferencias().stream().filter(x -> x != null && !Textos.vacio(x.texto())).toList());
        diferencias.add(new Diferencia(p.valor(), false, false, "modelo"));
        return new Entrada(entrada.caso(), entrada.conclusion(), entrada.similitudes(), diferencias, propuestas);
    }

    private static String lista(List<String> textos) {
        List<String> conTexto = textos.stream().filter(t -> !Textos.vacio(t)).map(t -> "«" + t.strip() + "»").toList();
        return conTexto.isEmpty() ? "(ninguna)" : String.join(", ", conTexto);
    }
}
