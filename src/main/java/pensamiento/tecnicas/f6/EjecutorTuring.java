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
import pensamiento.tecnicas.f2.Marcas;

/**
 * T37 · Test de Turing ideológico (Caplan 2011). Escribes la postura contraria como la defendería quien la cree y una
 * rúbrica en código mira caricatura, omisión y tono. Ningún puntaje sale del modelo: en este hito T37 no lo usa. Las
 * reglas están en docs/ejemplos/T37.md.
 */
@Component
public class EjecutorTuring implements Ejecutor<EjecutorTuring.Config, EjecutorTuring.Entrada, ResultadoTuring> {

    public static final IdTecnica ID = IdTecnica.de("T37");
    public static final int VERSION_ESQUEMA = 1;

    /** Marcas de burla, en minúscula y sin tildes. */
    public static final List<String> BURLAS = List.of("ridiculo", "ridicula", "absurdo", "absurda", "tonto", "tonta", "tonteria", "estupido", "estupida",
            "ingenuo", "ingenua", "obviamente", "cualquiera sabe", "jaja", "pobrecitos", "ni siquiera saben");

    public static final String MOTIVO = "%d de 100 con umbral %d. La rúbrica mira palabras, no la postura real de nadie: pregúntale a alguien que la sostenga.";

    /** Configuración de T37, versión de esquema 1. Los tres pesos suman 100. */
    public record Config(int pesoCaricatura, int pesoOmision, int pesoTono, int umbral) {
    }

    /** @param clave una o más palabras separadas por coma que tendrían que aparecer si el texto lo incluye */
    public record Argumento(String texto, String clave) {
    }

    public record Entrada(String postura, String texto, String steelman, List<Argumento> argumentos) {
        public Entrada {
            argumentos = argumentos == null ? List.of() : List.copyOf(argumentos);
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
    public Tipos<Config, Entrada, ResultadoTuring> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoTuring.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        for (int peso : List.of(config.pesoCaricatura(), config.pesoOmision(), config.pesoTono())) {
            if (peso < 0 || peso > 100) {
                errores.add(new Validacion.Error("config.pesoCaricatura", "Cada peso va de 0 a 100."));
                break;
            }
        }
        if (config.pesoCaricatura() + config.pesoOmision() + config.pesoTono() != 100) {
            errores.add(new Validacion.Error("config.pesoCaricatura", "Los pesos de caricatura, omisión y tono tienen que sumar 100."));
        } else if (config.pesoCaricatura() + config.pesoTono() == 0) {
            errores.add(new Validacion.Error("config.pesoCaricatura", "Caricatura y tono no pueden pesar 0 a la vez."));
        }
        if (config.umbral() < 0 || config.umbral() > 100) {
            errores.add(new Validacion.Error("config.umbral", "El umbral va de 0 a 100."));
        }
        if (Textos.vacio(entrada.postura())) {
            errores.add(new Validacion.Error("postura", "Escribe de quién es la postura que imitas."));
        }
        if (Textos.vacio(entrada.texto())) {
            errores.add(new Validacion.Error("texto", "Escribe la postura como la defendería quien la cree."));
        }
        if (entrada.argumentos().stream().anyMatch(a -> Textos.vacio(a.texto()) || Textos.partesPorComa(a.clave() == null ? "" : a.clave()).isEmpty())) {
            errores.add(new Validacion.Error("argumentos", "Cada argumento de referencia necesita su texto y al menos una clave."));
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoTuring> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        String texto = entrada.texto().strip();
        List<String> caricaturas = Marcas.todas(texto, EjecutorSteelman.CARICATURAS);
        List<String> burlas = Marcas.todas(texto, BURLAS);
        int caricatura = porMarcas(config.pesoCaricatura(), caricaturas.size());
        int tono = porMarcas(config.pesoTono(), burlas.size());
        List<String> palabras = palabras(texto);
        List<ResultadoTuring.Argumento> argumentos = new ArrayList<>();
        List<Pendiente> pendientes = new ArrayList<>();
        int cubiertos = 0;
        for (Argumento a : entrada.argumentos()) {
            Optional<String> clave = Textos.partesPorComa(a.clave()).stream()
                    .filter(c -> palabras.stream().anyMatch(p -> p.startsWith(Textos.plegar(c)))).findFirst();
            argumentos.add(new ResultadoTuring.Argumento(a.texto().strip(), clave.isPresent(), clave.orElse(null)));
            if (clave.isPresent()) {
                cubiertos++;
            } else {
                pendientes.add(new Pendiente(TipoPendiente.REVISION, Optional.empty(), Optional.empty(), "Incluir el argumento: " + a.texto().strip()));
            }
        }
        Integer omision;
        int puntaje;
        if (argumentos.isEmpty()) {
            omision = null;
            puntaje = dividir((caricatura + tono) * 100, config.pesoCaricatura() + config.pesoTono());
        } else {
            omision = dividir(config.pesoOmision() * cubiertos, argumentos.size());
            puntaje = caricatura + omision + tono;
        }
        boolean aprueba = puntaje >= config.umbral();
        int omitidos = argumentos.size() - cubiertos;
        String resumen = puntaje + " de 100 · " + (aprueba ? "aprueba" : "no aprueba") + " el umbral de " + config.umbral() + " · "
                + (omision == null ? "omisión sin medir." : Textos.contar(omitidos, "omisión", "omisiones") + ".");
        List<AfirmacionConRol> afirmaciones = List.of(new AfirmacionConRol(ctx.nuevoId().get(), texto, TipoAfirmacion.JUICIO_DE_VALOR,
                RolAfirmacion.POSTURA, SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
        ResultadoTuring valor = new ResultadoTuring(entrada.postura().strip(), texto, Textos.vacio(entrada.steelman()) ? null : entrada.steelman().strip(),
                puntaje, config.umbral(), aprueba, caricatura, config.pesoCaricatura(), omision, config.pesoOmision(), tono, config.pesoTono(), caricaturas,
                burlas, argumentos, String.format(MOTIVO, puntaje, config.umbral()), resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen);
    }

    /** El peso con 0 marcas, la mitad con 1 y 0 con 2 o más. */
    static int porMarcas(int peso, int marcas) {
        return marcas == 0 ? peso : marcas == 1 ? dividir(peso, 2) : 0;
    }

    /** División entera redondeada al más cercano, la mitad hacia arriba (para numeradores y divisores positivos). */
    static int dividir(int numerador, int divisor) {
        return (2 * numerador + divisor) / (2 * divisor);
    }

    /** Las palabras del texto, en minúscula y sin tildes. */
    static List<String> palabras(String texto) {
        return java.util.Arrays.stream(Textos.plegar(texto).split("[^\\p{L}\\p{N}]+")).filter(p -> !p.isEmpty()).toList();
    }

    @Override
    public ResultadoTuring migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA + "; se pidió migrar desde la " + desdeVersion);
    }
}
