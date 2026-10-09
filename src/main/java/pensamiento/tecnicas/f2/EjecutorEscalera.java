package pensamiento.tecnicas.f2;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import pensamiento.catalogo.BancoSocratico;
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
 * T10 · Escalera de inferencia (Argyris 1982; Senge 1990). Seis peldaños de los datos a la acción; la regla marca el
 * peldaño débil por una marca de salto o porque nadie lo comprobó. No usa el modelo; en el Consejero, el modo escalera
 * recorre los mismos peldaños con un turno por peldaño ({@link #recorrer}). Las reglas están en docs/ejemplos/T10.md.
 */
@Component
public class EjecutorEscalera implements Ejecutor<EjecutorEscalera.Config, EjecutorEscalera.Entrada, ResultadoEscalera> {

    public static final IdTecnica ID = IdTecnica.de("T10");
    public static final int VERSION_ESQUEMA = 1;

    /** Los seis peldaños, de abajo hacia arriba. */
    public enum Peldano {
        DATOS, SELECCION, INTERPRETACION, SUPUESTOS, CONCLUSION, ACCION;

        @Override
        public String toString() {
            return name().toLowerCase();
        }

        /** Datos, selección, interpretación y supuestos se pueden comprobar; conclusión y acción salen de los de abajo. */
        public boolean comprobable() {
            return ordinal() <= SUPUESTOS.ordinal();
        }
    }

    public enum Sentido {
        SUBIR, BAJAR;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** Configuración de T10, versión de esquema 1. */
    public record Config(List<Peldano> peldanos, Sentido sentido) {
        public Config {
            peldanos = peldanos == null ? List.of() : List.copyOf(peldanos);
        }
    }

    public record Entrada(String revisa, String datos, String seleccion, String interpretacion, String supuestos, String conclusion, String accion,
                          List<Peldano> comprobados) {
        public Entrada {
            comprobados = comprobados == null ? List.of() : List.copyOf(comprobados);
        }

        public String texto(Peldano p) {
            return switch (p) {
                case DATOS -> datos;
                case SELECCION -> seleccion;
                case INTERPRETACION -> interpretacion;
                case SUPUESTOS -> supuestos;
                case CONCLUSION -> conclusion;
                case ACCION -> accion;
            };
        }
    }

    private final BancoSocratico banco;

    public EjecutorEscalera() {
        this(BancoSocratico.delCatalogo());
    }

    public EjecutorEscalera(BancoSocratico banco) {
        this.banco = banco;
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
    public Tipos<Config, Entrada, ResultadoEscalera> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoEscalera.class);
    }

    /** Los peldaños activos en el orden del sentido. */
    public static List<Peldano> enOrden(Config config) {
        List<Peldano> orden = new ArrayList<>();
        for (Peldano p : Peldano.values()) {
            if (config.peldanos().contains(p)) {
                orden.add(p);
            }
        }
        if (config.sentido() == Sentido.BAJAR) {
            java.util.Collections.reverse(orden);
        }
        return orden;
    }

    /**
     * El modo escalera del Consejero: con n respuestas dadas, el peldaño que toca es el n+1 del orden del sentido, o
     * ninguno (el cierre) si ya se recorrieron todos. Un peldaño por turno y nunca vuelve atrás.
     */
    public static Optional<Peldano> siguiente(Config config, int respuestas) {
        List<Peldano> orden = enOrden(config);
        return respuestas < orden.size() ? Optional.of(orden.get(respuestas)) : Optional.empty();
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.peldanos().size() < 3) {
            errores.add(new Validacion.Error("config.peldanos", "Activa al menos tres peldaños."));
        }
        if (config.sentido() == null) {
            errores.add(new Validacion.Error("config.sentido", "Elige el sentido."));
        }
        if (Textos.vacio(entrada.revisa())) {
            errores.add(new Validacion.Error("revisa", "Escribe lo que quieres revisar."));
        }
        if (errores.isEmpty() && enOrden(config).stream().allMatch(p -> Textos.vacio(entrada.texto(p)))) {
            errores.add(new Validacion.Error("datos", "Llena al menos un peldaño."));
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoEscalera> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        List<Peldano> orden = enOrden(config);
        Peldano debil = null;
        String motivo = null;
        for (Peldano p : orden) {
            Optional<String> marca = Marcas.primera(entrada.texto(p), banco.marcas("salto"));
            if (!Textos.vacio(entrada.texto(p)) && marca.isPresent()) {
                debil = p;
                motivo = "Tiene una marca de salto: «" + marca.get() + "».";
                break;
            }
        }
        if (debil == null) {
            for (Peldano p : orden) {
                if (p.comprobable() && !Textos.vacio(entrada.texto(p)) && !entrada.comprobados().contains(p)) {
                    debil = p;
                    motivo = "Nadie lo comprobó con otra persona ni con un dato.";
                    break;
                }
            }
        }
        List<ResultadoEscalera.Peldano> peldanos = new ArrayList<>();
        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        List<Pendiente> pendientes = new ArrayList<>();
        int llenos = 0;
        for (Peldano p : orden) {
            String texto = Textos.vacio(entrada.texto(p)) ? null : entrada.texto(p).strip();
            String nombre = banco.peldano(p.toString()).nombre();
            String estado = texto == null ? "pendiente" : p == debil ? "peldaño débil" : "lleno";
            if (texto != null) {
                llenos++;
                Optional<java.util.UUID> id = afirmacion(p, texto, ctx, afirmaciones);
                if (p == debil) {
                    pendientes.add(new Pendiente(TipoPendiente.VERIFICACION, id, Optional.empty(), "Comprobar el peldaño " + nombre.toLowerCase() + ": " + texto));
                }
            }
            peldanos.add(new ResultadoEscalera.Peldano(p.toString(), p.ordinal() + 1, nombre, texto, estado, entrada.comprobados().contains(p),
                    p == debil ? motivo : null));
        }
        String nota = debil == null ? "Sin peldaño débil por las reglas: revisa tú si cada paso se sigue del anterior." : null;
        String resumen = llenos + " de " + orden.size() + " peldaños · "
                + (debil == null ? "sin peldaño débil por las reglas." : "débil: " + banco.peldano(debil.toString()).nombre().toLowerCase() + ".");
        ResultadoEscalera valor = new ResultadoEscalera(entrada.revisa().strip(), config.sentido().toString(), peldanos, llenos,
                debil == null ? null : debil.toString(), nota, resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen);
    }

    /** Datos, supuestos, conclusión y acción producen afirmaciones; selección e interpretación no. */
    private static Optional<java.util.UUID> afirmacion(Peldano p, String texto, Contexto ctx, List<AfirmacionConRol> afirmaciones) {
        RolAfirmacion rol;
        TipoAfirmacion tipo = TipoAfirmacion.HECHO;
        switch (p) {
            case DATOS -> rol = RolAfirmacion.PREMISA;
            case SUPUESTOS -> rol = RolAfirmacion.SUPUESTO;
            case CONCLUSION -> rol = RolAfirmacion.CONCLUSION;
            case ACCION -> {
                rol = RolAfirmacion.OPCION;
                tipo = TipoAfirmacion.JUICIO_DE_VALOR;
            }
            default -> {
                return Optional.empty();
            }
        }
        java.util.UUID id = ctx.nuevoId().get();
        afirmaciones.add(new AfirmacionConRol(id, texto, tipo, rol, SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
        return Optional.of(id);
    }

    @Override
    public ResultadoEscalera migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA + "; se pidió migrar desde la " + desdeVersion);
    }
}
