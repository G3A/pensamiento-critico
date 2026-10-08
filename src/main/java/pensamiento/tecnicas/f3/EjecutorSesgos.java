package pensamiento.tecnicas.f3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import pensamiento.catalogo.CatalogoJson;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.nucleo.Validacion;
import pensamiento.tecnicas.comun.Textos;

/**
 * T14 · Sesgos cognitivos (Tversky y Kahneman 1974). Recorre los sesgos activos que aplican al contexto y marca como
 * probable el que tiene señal en el texto o al que la persona respondió que sí; nunca dice "sin sesgos". No usa IA.
 * Las reglas están en docs/ejemplos/T14.md; el catálogo de sesgos, en catalogo/sesgos.json.
 */
@Component
public class EjecutorSesgos implements Ejecutor<EjecutorSesgos.Config, EjecutorSesgos.Entrada, ResultadoSesgos> {

    public static final IdTecnica ID = IdTecnica.de("T14");
    public static final int VERSION_ESQUEMA = 1;

    /** Señales en el texto, en minúscula y sin tildes, por sesgo: reglas léxicas, como las de T13. */
    static final Map<String, List<String>> SENALES = Map.of(
            "anclaje", List.of("primer precio", "el primero que vimos", "lo que pedian al principio", "el precio inicial", "la primera oferta"),
            "costo_hundido", List.of("ya gastamos", "ya gaste", "ya invertimos", "ya inverti", "ya pagamos", "despues de todo lo que"),
            "confirmacion", List.of("solo lei", "solo busque", "solo buscamos", "confirma lo que", "como ya sabia"),
            "exceso_confianza", List.of("seguro que", "sin duda", "no hay forma de que", "imposible que"),
            "disponibilidad", List.of("acabo de ver", "vi en las noticias", "le paso a un vecino", "me contaron que a"),
            "statu_quo", List.of("siempre lo hemos hecho", "mejor no cambiar", "asi ha sido siempre", "para que cambiar"),
            "arrastre", List.of("todo el mundo", "todos lo estan", "todos los vecinos", "la mayoria ya"),
            "halo", List.of("como es tan", "se ve tan", "como es muy", "parece tan"));

    public static final String SIN_SENAL = "No lo marcaste y el texto no lo muestra.";

    public enum ContextoDeUso {
        DECISION, LECTURA;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public enum Modo {
        QUIZ, LISTA;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** Configuración de T14, versión de esquema 1. El modo solo cambia el formulario. */
    public record Config(List<String> sesgos, ContextoDeUso contexto, Modo modo) {
        public Config {
            sesgos = sesgos == null ? List.of() : List.copyOf(sesgos);
        }
    }

    /** @param senales los sesgos a cuya pregunta la persona respondió que sí */
    public record Entrada(String situacion, List<String> senales) {
        public Entrada {
            senales = senales == null ? List.of() : List.copyOf(senales);
        }
    }

    private final List<CatalogoJson.Sesgo> catalogo;

    public EjecutorSesgos() {
        this(new CatalogoJson().sesgos());
    }

    public EjecutorSesgos(List<CatalogoJson.Sesgo> catalogo) {
        this.catalogo = List.copyOf(catalogo);
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
    public Tipos<Config, Entrada, ResultadoSesgos> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoSesgos.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.sesgos().isEmpty()) {
            errores.add(new Validacion.Error("config.sesgos", "Activa al menos un sesgo."));
        }
        if (config.sesgos().stream().anyMatch(s -> catalogo.stream().noneMatch(c -> c.id().equals(s)))) {
            errores.add(new Validacion.Error("config.sesgos", "Hay un sesgo que no está en el catálogo."));
        }
        if (config.contexto() == null) {
            errores.add(new Validacion.Error("config.contexto", "Elige el contexto."));
        }
        if (Textos.vacio(entrada.situacion())) {
            errores.add(new Validacion.Error("situacion", "Describe la decisión o lo que leíste."));
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoSesgos> ejecutar(Config config, Entrada entrada, pensamiento.nucleo.Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        String situacion = entrada.situacion().strip();
        List<ResultadoSesgos.SesgoRevisado> probables = new ArrayList<>();
        List<ResultadoSesgos.SesgoRevisado> sinSenal = new ArrayList<>();
        for (CatalogoJson.Sesgo s : catalogo) {
            if (!config.sesgos().contains(s.id()) || (config.contexto() == ContextoDeUso.LECTURA && !s.lectura())) {
                continue;
            }
            Optional<String> enTexto = Textos.primeraFrase(situacion, SENALES.getOrDefault(s.id(), List.of()));
            if (enTexto.isPresent()) {
                probables.add(new ResultadoSesgos.SesgoRevisado(s.id(), s.nombre(), true, "Encontré «" + enTexto.get() + "» en tu situación.", s.antidoto()));
            } else if (entrada.senales().contains(s.id())) {
                probables.add(new ResultadoSesgos.SesgoRevisado(s.id(), s.nombre(), true, "Respondiste que sí: " + s.pregunta(), s.antidoto()));
            } else {
                sinSenal.add(new ResultadoSesgos.SesgoRevisado(s.id(), s.nombre(), false, SIN_SENAL, s.antidoto()));
            }
        }
        List<ResultadoSesgos.SesgoRevisado> revisados = new ArrayList<>(probables);
        revisados.addAll(sinSenal);
        List<Pendiente> pendientes = probables.stream().map(p -> new Pendiente(TipoPendiente.REVISION, Optional.empty(), Optional.empty(),
                "Aplicar el antídoto contra " + p.nombre().toLowerCase() + ": " + p.antidoto())).toList();
        String resumen = probables.isEmpty()
                ? "Ningún sesgo con señal de " + revisados.size() + " revisados."
                : Textos.contar(probables.size(), "sesgo probable", "sesgos probables") + " de " + revisados.size() + " revisados: "
                + Textos.enumerar(probables.stream().map(p -> p.nombre().toLowerCase()).toList()) + ".";
        ResultadoSesgos valor = new ResultadoSesgos(situacion, config.contexto().toString(), revisados,
                probables.stream().map(ResultadoSesgos.SesgoRevisado::antidoto).toList(), probables.size(), resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, List.of(), pendientes, resumen);
    }

    @Override
    public ResultadoSesgos migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }
}
