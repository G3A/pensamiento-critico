package pensamiento.tecnicas.f2;

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

/**
 * T11 · Falsación y "qué tendría que ser cierto" (Popper 1934; Lafley y Martin 2013). Las condiciones de las que depende
 * una postura, verificables o no, y lo que haría cambiar de opinión. Cada condición verificable queda como pendiente de
 * verificación hasta que llegue la ficha del hito 6. No usa el modelo. Las reglas están en docs/ejemplos/T11.md.
 */
@Component
public class EjecutorFalsacion implements Ejecutor<EjecutorFalsacion.Config, EjecutorFalsacion.Entrada, ResultadoFalsacion> {

    public static final IdTecnica ID = IdTecnica.de("T11");
    public static final int VERSION_ESQUEMA = 1;

    /** Configuración de T11, versión de esquema 1. */
    public record Config(int condicionesMinimas, boolean exigirVerificable) {
    }

    public record Condicion(String texto, boolean verificable, String como) {
    }

    public record Entrada(String postura, List<Condicion> condiciones, String cambiaria) {
        public Entrada {
            condiciones = condiciones == null ? List.of() : List.copyOf(condiciones);
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
    public Tipos<Config, Entrada, ResultadoFalsacion> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoFalsacion.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.condicionesMinimas() < 1 || config.condicionesMinimas() > 5) {
            errores.add(new Validacion.Error("config.condicionesMinimas", "Las condiciones mínimas van de 1 a 5."));
        }
        if (Textos.vacio(entrada.postura())) {
            errores.add(new Validacion.Error("postura", "Escribe la postura."));
        }
        if (entrada.condiciones().isEmpty()) {
            errores.add(new Validacion.Error("condiciones", "Escribe al menos una condición."));
        } else if (entrada.condiciones().stream().anyMatch(c -> Textos.vacio(c.texto()))) {
            errores.add(new Validacion.Error("condiciones", "Cada condición necesita su texto."));
        }
        if (Textos.vacio(entrada.cambiaria())) {
            errores.add(new Validacion.Error("cambiaria", "Escribe qué te haría cambiar de opinión."));
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoFalsacion> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        String postura = entrada.postura().strip();
        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        afirmaciones.add(new AfirmacionConRol(ctx.nuevoId().get(), postura, TipoAfirmacion.HECHO, RolAfirmacion.POSTURA, SentidoAfirmacion.PRODUCIDA,
                OrigenAfirmacion.USUARIO));
        List<ResultadoFalsacion.Condicion> condiciones = new ArrayList<>();
        List<Pendiente> pendientes = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        int n = entrada.condiciones().size();
        if (n < config.condicionesMinimas()) {
            int faltan = config.condicionesMinimas() - n;
            avisos.add("Falta" + (faltan == 1 ? "" : "n") + " " + Textos.contar(faltan, "condición", "condiciones") + ": la configuración pide "
                    + config.condicionesMinimas() + ".");
        }
        List<String> noVerificables = new ArrayList<>();
        List<String> sinComo = new ArrayList<>();
        int verificables = 0;
        for (int i = 0; i < n; i++) {
            Condicion c = entrada.condiciones().get(i);
            String texto = c.texto().strip();
            String como = Textos.vacio(c.como()) ? null : c.como().strip();
            String estado;
            if (!c.verificable()) {
                estado = "no verificable";
                noVerificables.add(texto);
            } else if (config.exigirVerificable() && como == null) {
                estado = "verificable · falta cómo";
                sinComo.add(texto);
            } else {
                estado = "verificable";
            }
            UUID id = ctx.nuevoId().get();
            afirmaciones.add(new AfirmacionConRol(id, texto, c.verificable() ? TipoAfirmacion.HECHO : TipoAfirmacion.JUICIO_DE_VALOR, RolAfirmacion.SUPUESTO,
                    SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
            if (c.verificable()) {
                verificables++;
                pendientes.add(new Pendiente(TipoPendiente.VERIFICACION, Optional.of(id), Optional.empty(), "Verificar la condición: " + texto));
            }
            condiciones.add(new ResultadoFalsacion.Condicion("C" + (i + 1), texto, estado, como, id));
        }
        if (config.exigirVerificable()) {
            noVerificables.forEach(t -> avisos.add("«" + t + "» no es verificable: reescríbela como algo que se pueda observar."));
            sinComo.forEach(t -> avisos.add("Falta cómo verificar: «" + t + "»."));
        }
        String cambiaria = entrada.cambiaria().strip();
        afirmaciones.add(new AfirmacionConRol(ctx.nuevoId().get(), cambiaria, TipoAfirmacion.HECHO, RolAfirmacion.CONDICION_FALSACION,
                SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
        List<String> acciones = List.of("Me haría cambiar de opinión: " + cambiaria,
                verificables == 1 ? "1 condición queda como pendiente de verificación (la ficha llega en el hito 6)."
                        : verificables + " condiciones quedan como pendientes de verificación (la ficha llega en el hito 6).");
        String resumen = Textos.contar(n, "condición", "condiciones") + " · " + Textos.contar(verificables, "verificable", "verificables") + " · "
                + verificables + " a verificación.";
        ResultadoFalsacion valor = new ResultadoFalsacion(postura, condiciones, cambiaria, avisos, acciones, verificables, resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen);
    }

    @Override
    public ResultadoFalsacion migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA + "; se pidió migrar desde la " + desdeVersion);
    }
}
