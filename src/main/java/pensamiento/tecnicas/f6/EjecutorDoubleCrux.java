package pensamiento.tecnicas.f6;

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
import pensamiento.tecnicas.f7.EjecutorArbolMece;

/**
 * T38 · Double crux (CFAR 2016). Dos posturas, los hechos de los que depende cada una y el hecho común cuya verdad haría
 * cambiar a las dos; ese hecho queda como pendiente de verificación hasta la ficha del hito 6. La regla de solapes de T42
 * sugiere pares que hablan de lo mismo. No usa el modelo. Las reglas están en docs/ejemplos/T38.md.
 */
@Component
public class EjecutorDoubleCrux implements Ejecutor<EjecutorDoubleCrux.Config, EjecutorDoubleCrux.Entrada, ResultadoDoubleCrux> {

    public static final IdTecnica ID = IdTecnica.de("T38");
    public static final int VERSION_ESQUEMA = 1;

    /** Configuración de T38, versión de esquema 1. */
    public record Config(int maximoCruxes, boolean exigirVerificables) {
    }

    public record Hecho(String texto) {
    }

    public record Crux(String hecho, boolean cambiaA, boolean cambiaB, boolean verificable, String como) {
    }

    public record Entrada(String quienA, String posturaA, List<Hecho> dependeA, String quienB, String posturaB, List<Hecho> dependeB, List<Crux> cruxes) {
        public Entrada {
            dependeA = dependeA == null ? List.of() : List.copyOf(dependeA);
            dependeB = dependeB == null ? List.of() : List.copyOf(dependeB);
            cruxes = cruxes == null ? List.of() : List.copyOf(cruxes);
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
    public Tipos<Config, Entrada, ResultadoDoubleCrux> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoDoubleCrux.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.maximoCruxes() < 1 || config.maximoCruxes() > 3) {
            errores.add(new Validacion.Error("config.maximoCruxes", "El máximo de cruxes va de 1 a 3."));
        }
        if (Textos.vacio(entrada.quienA()) || Textos.vacio(entrada.posturaA())) {
            errores.add(new Validacion.Error("posturaA", "Escribe quién es el lado A y qué sostiene."));
        }
        if (Textos.vacio(entrada.quienB()) || Textos.vacio(entrada.posturaB())) {
            errores.add(new Validacion.Error("posturaB", "Escribe quién es el lado B y qué sostiene."));
        }
        if (entrada.dependeA().isEmpty() || entrada.dependeA().stream().anyMatch(h -> Textos.vacio(h.texto()))) {
            errores.add(new Validacion.Error("dependeA", "Escribe al menos un hecho del que depende el lado A."));
        }
        if (entrada.dependeB().isEmpty() || entrada.dependeB().stream().anyMatch(h -> Textos.vacio(h.texto()))) {
            errores.add(new Validacion.Error("dependeB", "Escribe al menos un hecho del que depende el lado B."));
        }
        if (entrada.cruxes().stream().anyMatch(c -> Textos.vacio(c.hecho()))) {
            errores.add(new Validacion.Error("cruxes", "Cada crux necesita su hecho."));
        }
        if (entrada.cruxes().size() > config.maximoCruxes()) {
            errores.add(new Validacion.Error("cruxes", "Hay " + entrada.cruxes().size() + " cruxes y la configuración permite " + config.maximoCruxes() + "."));
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoDoubleCrux> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        ResultadoDoubleCrux.Lado a = lado(entrada.quienA(), entrada.posturaA(), entrada.dependeA(), ctx, afirmaciones);
        ResultadoDoubleCrux.Lado b = lado(entrada.quienB(), entrada.posturaB(), entrada.dependeB(), ctx, afirmaciones);
        List<String> sugerencias = new ArrayList<>();
        for (String x : a.depende()) {
            for (String y : b.depende()) {
                if (EjecutorArbolMece.seSolapan(x, y)) {
                    sugerencias.add("Posible crux: «" + x + "» y «" + y + "» hablan de lo mismo.");
                }
            }
        }
        List<ResultadoDoubleCrux.Crux> cruxes = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        List<Pendiente> pendientes = new ArrayList<>();
        int comunes = 0;
        int aVerificacion = 0;
        for (Crux c : entrada.cruxes()) {
            String hecho = c.hecho().strip();
            boolean comun = c.cambiaA() && c.cambiaB();
            UUID id = ctx.nuevoId().get();
            afirmaciones.add(new AfirmacionConRol(id, hecho, TipoAfirmacion.HECHO, RolAfirmacion.CONDICION_FALSACION, SentidoAfirmacion.PRODUCIDA,
                    OrigenAfirmacion.USUARIO));
            if (comun) {
                comunes++;
            } else if (c.cambiaA() || c.cambiaB()) {
                avisos.add("«" + hecho + "» haría cambiar solo a un lado: no es un double crux.");
            } else {
                avisos.add("«" + hecho + "» no haría cambiar a ningún lado: no es un crux.");
            }
            if (config.exigirVerificables() && !c.verificable()) {
                avisos.add("La configuración exige cruxes verificables: reescribe «" + hecho + "» como algo que se pueda comprobar.");
            }
            if (comun && c.verificable()) {
                aVerificacion++;
                pendientes.add(new Pendiente(TipoPendiente.VERIFICACION, Optional.of(id), Optional.empty(), "Verificar el crux: " + hecho));
            }
            cruxes.add(new ResultadoDoubleCrux.Crux(hecho, c.cambiaA(), c.cambiaB(), comun, c.verificable(), Textos.vacio(c.como()) ? null : c.como().strip(), id));
        }
        String estado = comunes == 0 ? "sin crux común todavía" : Textos.contar(comunes, "crux común", "cruxes comunes");
        String resumen = comunes == 0 ? "Sin crux común todavía."
                : Textos.contar(comunes, "crux común", "cruxes comunes") + " · " + aVerificacion + " a verificación.";
        ResultadoDoubleCrux valor = new ResultadoDoubleCrux(a, b, cruxes, comunes, aVerificacion, sugerencias, avisos, estado, resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen);
    }

    private static ResultadoDoubleCrux.Lado lado(String quien, String postura, List<Hecho> depende, Contexto ctx, List<AfirmacionConRol> afirmaciones) {
        afirmaciones.add(new AfirmacionConRol(ctx.nuevoId().get(), postura.strip(), TipoAfirmacion.JUICIO_DE_VALOR, RolAfirmacion.POSTURA,
                SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
        List<String> hechos = new ArrayList<>();
        for (Hecho h : depende) {
            hechos.add(h.texto().strip());
            afirmaciones.add(new AfirmacionConRol(ctx.nuevoId().get(), h.texto().strip(), TipoAfirmacion.HECHO, RolAfirmacion.SUPUESTO,
                    SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
        }
        return new ResultadoDoubleCrux.Lado(quien.strip(), postura.strip(), hechos);
    }

    @Override
    public ResultadoDoubleCrux migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA + "; se pidió migrar desde la " + desdeVersion);
    }
}
