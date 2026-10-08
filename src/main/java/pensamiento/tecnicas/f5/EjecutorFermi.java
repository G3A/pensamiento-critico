package pensamiento.tecnicas.f5;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
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
 * T26 · Estimación de Fermi (Fermi, años 40). Multiplica los mínimos y los máximos de cada factor sin redondeos
 * intermedios, da la media geométrica del rango como valor central y señala el factor más incierto. No usa IA. Las reglas
 * están en docs/ejemplos/T26.md.
 */
@Component
public class EjecutorFermi implements Ejecutor<EjecutorFermi.Config, EjecutorFermi.Entrada, ResultadoFermi> {

    public static final IdTecnica ID = IdTecnica.de("T26");
    public static final int VERSION_ESQUEMA = 1;
    static final int TOPE_FACTORES = 8;
    static final BigDecimal CIEN = BigDecimal.valueOf(100);

    /** Configuración de T26, versión de esquema 1. */
    public record Config(int pasosMinimos, boolean exigirUnidades) {
    }

    /** @param porcentaje el número se divide por 100 (2 a 4 son 2% a 4%) */
    public record Factor(String texto, Integer minimo, Integer maximo, String unidad, boolean porcentaje) {
    }

    /** @param referencia un número conocido para comparar; nulo si no hay */
    public record Entrada(String pregunta, String unidad, List<Factor> factores, Integer referencia, String deDondeReferencia) {
        public Entrada {
            factores = factores == null ? List.of() : List.copyOf(factores);
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
    public Tipos<Config, Entrada, ResultadoFermi> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoFermi.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.pasosMinimos() < 1 || config.pasosMinimos() > TOPE_FACTORES) {
            errores.add(new Validacion.Error("config.pasosMinimos", "Los pasos mínimos van de 1 a " + TOPE_FACTORES + "."));
        }
        if (Textos.vacio(entrada.pregunta())) {
            errores.add(new Validacion.Error("pregunta", "Escribe la cantidad que quieres estimar."));
        }
        if (Textos.vacio(entrada.unidad())) {
            errores.add(new Validacion.Error("unidad", "Escribe la unidad del resultado: panes por día, litros por semana…"));
        }
        if (entrada.factores().isEmpty()) {
            errores.add(new Validacion.Error("factores", "Escribe al menos un factor."));
        } else if (entrada.factores().size() > TOPE_FACTORES) {
            errores.add(new Validacion.Error("factores", "Caben como máximo " + TOPE_FACTORES + " factores."));
        }
        for (int i = 0; i < entrada.factores().size(); i++) {
            Factor f = entrada.factores().get(i);
            String campo = "factores[" + i + "]";
            if (Textos.vacio(f.texto())) {
                errores.add(new Validacion.Error(campo + ".texto", "Escribe el factor F" + (i + 1) + "."));
            }
            if (f.minimo() == null || f.minimo() < 0) {
                errores.add(new Validacion.Error(campo + ".minimo", "El mínimo de F" + (i + 1) + " es un entero de 0 o más."));
            }
            if (f.maximo() == null || (f.minimo() != null && f.maximo() < f.minimo())) {
                errores.add(new Validacion.Error(campo + ".maximo", "El máximo de F" + (i + 1) + " no puede ser menor que el mínimo."));
            }
            if (config.exigirUnidades() && !f.porcentaje() && Textos.vacio(f.unidad())) {
                errores.add(new Validacion.Error(campo + ".unidad", "Escribe la unidad de F" + (i + 1) + "."));
            }
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoFermi> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        String unidad = entrada.unidad().strip();
        BigDecimal minimo = BigDecimal.ONE;
        BigDecimal maximo = BigDecimal.ONE;
        int masAncho = 0;
        BigDecimal anchoMayor = null;
        boolean sinPiso = false;
        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        UUID resultadoId = ctx.nuevoId().get();
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < entrada.factores().size(); i++) {
            Factor f = entrada.factores().get(i);
            BigDecimal min = valor(f.minimo(), f.porcentaje());
            BigDecimal max = valor(f.maximo(), f.porcentaje());
            minimo = minimo.multiply(min);
            maximo = maximo.multiply(max);
            if (f.minimo() == 0 && f.maximo() > 0) {
                // De 0 a algo es lo más ancho posible: gana el primero así.
                if (!sinPiso) {
                    sinPiso = true;
                    masAncho = i;
                }
            } else if (!sinPiso) {
                BigDecimal ancho = f.minimo() == 0 ? BigDecimal.ONE : max.divide(min, MathContext.DECIMAL64);
                if (anchoMayor == null || ancho.compareTo(anchoMayor) > 0) {
                    masAncho = i;
                    anchoMayor = ancho;
                }
            }
            UUID id = ctx.nuevoId().get();
            ids.add(id);
        }
        List<ResultadoFermi.FactorEn> factores = new ArrayList<>();
        for (int i = 0; i < entrada.factores().size(); i++) {
            Factor f = entrada.factores().get(i);
            String texto = f.texto().strip();
            afirmaciones.add(new AfirmacionConRol(ids.get(i), texto, TipoAfirmacion.DATO_ESTADISTICO, RolAfirmacion.SUPUESTO, SentidoAfirmacion.PRODUCIDA,
                    OrigenAfirmacion.USUARIO));
            factores.add(new ResultadoFermi.FactorEn(ids.get(i), "F" + (i + 1), texto, rango(f), i == masAncho));
        }
        String min = entero(minimo);
        String max = entero(maximo);
        String central = central(minimo, maximo);
        String estimacion = "Entre " + min + " y " + max + " " + unidad + ", central " + central;
        List<String> avisos = new ArrayList<>();
        if (minimo.signum() == 0) {
            avisos.add("El mínimo es 0: el rango no tiene piso. Revisa los factores que pueden ser 0.");
        } else if (maximo.divide(minimo, MathContext.DECIMAL64).compareTo(CIEN) > 0) {
            avisos.add("El rango va de " + min + " a " + max + ": más de 100 veces. Parte el factor más ancho en dos.");
        }
        int n = entrada.factores().size();
        if (n < config.pasosMinimos()) {
            avisos.add("Con " + Textos.contar(n, "factor", "factores") + ": la configuración pide al menos " + config.pasosMinimos()
                    + ". Descompón el factor más incierto.");
        }
        String referencia = null;
        if (entrada.referencia() != null) {
            BigDecimal r = BigDecimal.valueOf(entrada.referencia());
            boolean dentro = r.compareTo(minimo) >= 0 && r.compareTo(maximo) <= 0;
            referencia = entrada.referencia() + (dentro ? " está dentro del rango." : " está fuera del rango: revisa los factores o la referencia.");
        }
        List<Pendiente> pendientes = new ArrayList<>();
        if (sinPiso || anchoMayor.compareTo(BigDecimal.ONE) > 0) {
            pendientes.add(new Pendiente(TipoPendiente.VERIFICACION, Optional.of(ids.get(masAncho)), Optional.empty(),
                    "Buscar un dato para el factor más incierto: " + factores.get(masAncho).texto()));
        }
        afirmaciones.addFirst(new AfirmacionConRol(resultadoId, entrada.pregunta().strip() + ": " + Textos.comoClausula(estimacion),
                TipoAfirmacion.DATO_ESTADISTICO, RolAfirmacion.HIPOTESIS, SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
        String resumen = estimacion + " · " + Textos.contar(n, "factor", "factores") + ".";
        ResultadoFermi valor = new ResultadoFermi(entrada.pregunta().strip(), unidad, resultadoId, factores, min, max, central, referencia,
                Textos.vacio(entrada.deDondeReferencia()) ? null : entrada.deDondeReferencia().strip(), avisos, resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen);
    }

    private static BigDecimal valor(int numero, boolean porcentaje) {
        BigDecimal v = BigDecimal.valueOf(numero);
        return porcentaje ? v.divide(CIEN) : v;
    }

    /** "300 a 600 peatones por hora", "10 horas", "2% a 4%", "5%". */
    static String rango(Factor f) {
        String sufijo = f.porcentaje() ? "%" : "";
        String unidad = f.porcentaje() || Textos.vacio(f.unidad()) ? "" : " " + f.unidad().strip();
        if (f.minimo().equals(f.maximo())) {
            return f.minimo() + sufijo + unidad;
        }
        return f.minimo() + sufijo + " a " + f.maximo() + sufijo + unidad;
    }

    private static String entero(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    /** Media geométrica, a dos cifras significativas y nunca más fina que un entero (docs/ejemplos/T26.md, regla 3). */
    static String central(BigDecimal minimo, BigDecimal maximo) {
        BigDecimal media = minimo.multiply(maximo).sqrt(MathContext.DECIMAL64);
        BigDecimal redondeada = media.compareTo(BigDecimal.TEN) < 0 ? media.setScale(0, RoundingMode.HALF_UP)
                : media.round(new MathContext(2, RoundingMode.HALF_UP));
        return redondeada.toPlainString();
    }

    @Override
    public ResultadoFermi migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }
}
