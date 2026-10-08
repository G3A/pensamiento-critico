package pensamiento.tecnicas.f3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.Esquema;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.nucleo.Validacion;
import pensamiento.nucleo.puertos.RepositorioEsquemas;

/**
 * T13 · Falacias como esquemas fallidos (Hamblin 1970; Walton 1995), solo con reglas en este hito (RF-10).
 * Las reglas léxicas proponen el esquema y la pregunta crítica sin responder; la etiqueta de falacia la
 * confirma la persona (R06). Las reglas de cálculo están en docs/ejemplos/T13.md.
 */
@Component
public class EjecutorFalacias implements Ejecutor<ConfigFalacias, EntradaFalacias, ResultadoFalacias> {

    public static final IdTecnica ID = IdTecnica.de("T13");
    public static final int VERSION_ESQUEMA = 1;
    public static final int LARGO_TEXTO = 2000;

    private static final Pattern CODIGO = Pattern.compile("^M\\d{1,2}$");

    private final RepositorioEsquemas esquemas;

    public EjecutorFalacias(RepositorioEsquemas esquemas) {
        this.esquemas = esquemas;
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
    public Tipos<ConfigFalacias, EntradaFalacias, ResultadoFalacias> tipos() {
        return new Tipos<>(ConfigFalacias.class, EntradaFalacias.class, ResultadoFalacias.class);
    }

    @Override
    public Validacion validar(ConfigFalacias config, EntradaFalacias entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.esquemas().isEmpty()) {
            errores.add(new Validacion.Error("esquemas", "Activa al menos un esquema."));
        }
        for (String id : config.esquemas()) {
            if (!ReglasFalacias.ESQUEMAS.contains(id) || esquemas.porId(id).isEmpty()) {
                errores.add(new Validacion.Error("esquemas", "El esquema «" + id + "» no está en el catálogo."));
                break;
            }
        }
        if (config.sensibilidad() == null) {
            errores.add(new Validacion.Error("sensibilidad", "Elige la sensibilidad."));
        }
        if (entrada.texto() == null || entrada.texto().isBlank()) {
            errores.add(new Validacion.Error("texto", "Escribe o pega el texto que quieres revisar."));
        } else if (entrada.texto().length() > LARGO_TEXTO) {
            errores.add(new Validacion.Error("texto", "Como máximo " + LARGO_TEXTO + " caracteres."));
        }
        for (String codigo : entrada.confirmadas()) {
            if (codigo == null || !CODIGO.matcher(codigo).matches()) {
                errores.add(new Validacion.Error("confirmadas", "Las confirmaciones son códigos de marca como M1."));
                break;
            }
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoFalacias> ejecutar(ConfigFalacias config, EntradaFalacias entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        Set<String> confirmadas = new HashSet<>(entrada.confirmadas());
        List<ResultadoFalacias.Marca> marcas = new ArrayList<>();
        List<Pendiente> pendientes = new ArrayList<>();
        for (ReglasFalacias.Hallazgo h : ReglasFalacias.buscar(entrada.texto(), new HashSet<>(config.esquemas()))) {
            String codigo = "M" + (marcas.size() + 1);
            Esquema esquema = esquemas.porId(h.regla().esquema()).orElseThrow();
            Esquema.PreguntaCritica pregunta = esquema.pregunta(h.regla().pregunta()).orElseThrow(
                    () -> new IllegalStateException("El esquema " + esquema.id() + " no tiene la pregunta " + h.regla().pregunta()));
            ResultadoFalacias.Estado estado = confirmadas.contains(codigo) ? ResultadoFalacias.Estado.CONFIRMADA : ResultadoFalacias.Estado.PROPUESTA;
            marcas.add(new ResultadoFalacias.Marca(codigo, h.oracion().inicio(), h.oracion().fin(), h.oracion().texto(), esquema.id(),
                    esquema.nombre(), pregunta.numero(), pregunta.texto(), pregunta.falacia(), pregunta.comoResponder(), h.porque(), estado));
            if (estado == ResultadoFalacias.Estado.PROPUESTA) {
                pendientes.add(new Pendiente(TipoPendiente.REVISION, Optional.empty(), Optional.empty(),
                        "Responder la pregunta crítica de " + codigo + " (" + esquema.nombre() + "): " + pregunta.texto()));
            }
        }
        int nConfirmadas = (int) marcas.stream().filter(ResultadoFalacias.Marca::confirmada).count();
        String resumen = resumen(marcas, nConfirmadas);
        ResultadoFalacias valor = new ResultadoFalacias(entrada.texto(), config.mostrarPregunta(), marcas, nConfirmadas,
                marcas.size() - nConfirmadas, resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, List.of(), pendientes, resumen);
    }

    @Override
    public ResultadoFalacias migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }

    private static String resumen(List<ResultadoFalacias.Marca> marcas, int confirmadas) {
        if (marcas.isEmpty()) {
            return "Sin marcas: las reglas no reconocieron ningún esquema.";
        }
        int propuestas = marcas.size() - confirmadas;
        String inicio = marcas.size() + (marcas.size() == 1 ? " marca: " : " marcas: ");
        Set<String> etiquetas = new LinkedHashSet<>();
        marcas.stream().filter(ResultadoFalacias.Marca::confirmada).forEach(m -> etiquetas.add(m.falacia()));
        String falacias = switch (confirmadas) {
            case 0 -> "ninguna falacia confirmada";
            case 1 -> "1 falacia confirmada (" + String.join(", ", etiquetas) + ")";
            default -> confirmadas + " falacias confirmadas (" + String.join(", ", etiquetas) + ")";
        };
        String abiertas = switch (propuestas) {
            case 0 -> "ningún esquema con preguntas sin responder";
            case 1 -> "1 esquema con preguntas sin responder";
            default -> propuestas + " esquemas con preguntas sin responder";
        };
        return inicio + falacias + " y " + abiertas + ".";
    }
}
