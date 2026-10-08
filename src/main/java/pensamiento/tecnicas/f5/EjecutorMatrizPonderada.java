package pensamiento.tecnicas.f5;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

/**
 * T31 · Matriz de decisión ponderada (Kepner y Tregoe 1965; Pugh 1981). Puntúa cada opción por criterio, multiplica por
 * el peso y ordena; con probabilidad × impacto, multiplica cada total por su probabilidad. Dice cuánto tiene que cambiar
 * un peso para que cambie el ganador. No usa IA. Las reglas están en docs/ejemplos/T31.md.
 */
@Component
public class EjecutorMatrizPonderada implements Ejecutor<EjecutorMatrizPonderada.Config, EjecutorMatrizPonderada.Entrada, ResultadoMatriz> {

    public static final IdTecnica ID = IdTecnica.de("T31");
    public static final int VERSION_ESQUEMA = 1;
    static final int TOPE = 8;

    public enum Escala {
        UNO_A_CINCO("1a5", 5), UNO_A_DIEZ("1a10", 10);

        private final String valor;
        private final int maximo;

        Escala(String valor, int maximo) {
            this.valor = valor;
            this.maximo = maximo;
        }

        public int maximo() {
            return maximo;
        }

        @Override
        public String toString() {
            return valor;
        }
    }

    /** Configuración de T31, versión de esquema 1. */
    public record Config(Escala escala, boolean probabilidadPorImpacto) {
    }

    public record Criterio(String texto, Integer peso) {
    }

    /** @param puntajes uno por criterio, en su orden; @param probabilidad de 0 a 100, solo con probabilidad × impacto */
    public record Opcion(String texto, List<String> puntajes, Integer probabilidad) {
        public Opcion {
            puntajes = puntajes == null ? List.of() : java.util.Collections.unmodifiableList(new ArrayList<>(puntajes));
        }
    }

    public record Entrada(String pregunta, List<Criterio> criterios, List<Opcion> opciones) {
        public Entrada {
            criterios = criterios == null ? List.of() : List.copyOf(criterios);
            opciones = opciones == null ? List.of() : List.copyOf(opciones);
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
    public Tipos<Config, Entrada, ResultadoMatriz> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoMatriz.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.escala() == null) {
            errores.add(new Validacion.Error("config.escala", "Elige la escala: 1 a 5 o 1 a 10."));
        }
        if (Textos.vacio(entrada.pregunta())) {
            errores.add(new Validacion.Error("pregunta", "Escribe la decisión."));
        }
        if (entrada.criterios().isEmpty() || entrada.criterios().size() > TOPE) {
            errores.add(new Validacion.Error("criterios", "Escribe de 1 a " + TOPE + " criterios."));
        }
        if (entrada.opciones().size() < 2 || entrada.opciones().size() > TOPE) {
            errores.add(new Validacion.Error("opciones", "Escribe de 2 a " + TOPE + " opciones."));
        }
        for (int c = 0; c < entrada.criterios().size(); c++) {
            Criterio k = entrada.criterios().get(c);
            if (Textos.vacio(k.texto())) {
                errores.add(new Validacion.Error("criterios[" + c + "].texto", "Escribe el criterio K" + (c + 1) + "."));
            }
            if (k.peso() == null || k.peso() < 1 || k.peso() > 10) {
                errores.add(new Validacion.Error("criterios[" + c + "].peso", "El peso de K" + (c + 1) + " va de 1 a 10."));
            }
        }
        int maximo = config.escala() == null ? 10 : config.escala().maximo();
        for (int o = 0; o < entrada.opciones().size(); o++) {
            Opcion op = entrada.opciones().get(o);
            String campo = "opciones[" + o + "]";
            if (Textos.vacio(op.texto())) {
                errores.add(new Validacion.Error(campo + ".texto", "Escribe la opción O" + (o + 1) + "."));
            }
            for (int c = 0; c < entrada.criterios().size(); c++) {
                Integer p = c < op.puntajes().size() ? entero(op.puntajes().get(c)) : null;
                if (p == null || p < 1 || p > maximo) {
                    errores.add(new Validacion.Error(campo + ".puntajes[" + c + "]", "Puntúa O" + (o + 1) + " en K" + (c + 1) + " de 1 a " + maximo + "."));
                }
            }
            if (config.probabilidadPorImpacto() && (op.probabilidad() == null || op.probabilidad() < 0 || op.probabilidad() > 100)) {
                errores.add(new Validacion.Error(campo + ".probabilidad", "La probabilidad de O" + (o + 1) + " va de 0 a 100."));
            }
        }
        return new Validacion(errores);
    }

    static Integer entero(String texto) {
        try {
            return texto == null ? null : Integer.valueOf(texto.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public Resultado<ResultadoMatriz> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        List<Integer> pesos = entrada.criterios().stream().map(Criterio::peso).toList();
        List<List<Integer>> puntajes = entrada.opciones().stream()
                .map(o -> o.puntajes().subList(0, pesos.size()).stream().map(EjecutorMatrizPonderada::entero).toList()).toList();
        boolean pxi = config.probabilidadPorImpacto();
        List<Integer> factores = entrada.opciones().stream().map(o -> pxi ? o.probabilidad() : 1).toList();
        MatrizPonderada.Calculo calculo = MatrizPonderada.calcular(pesos, puntajes, factores);
        List<String> criterios = entrada.criterios().stream().map(k -> k.texto().strip()).toList();
        List<String> opciones = entrada.opciones().stream().map(o -> o.texto().strip()).toList();
        ResultadoMatriz valor = ResultadoMatriz.armar("Matriz de decisión ponderada", entrada.pregunta().strip(), criterios, pesos, opciones, puntajes, calculo,
                pxi ? entrada.opciones().stream().map(Opcion::probabilidad).toList() : null, v -> pxi ? conDecimal(v) : String.valueOf(v),
                "gana", "ningún peso entre 0 y 10 cambia el ganador");
        List<Pendiente> pendientes = new ArrayList<>();
        String resumen;
        if (calculo.ganador().isPresent()) {
            ResultadoMatriz.FilaOpcion primera = valor.filas().getFirst();
            resumen = "1º " + primera.opcion() + " (" + primera.valor() + ") · sensibilidad " + calculo.nivel() + ".";
        } else {
            List<String> empatadas = valor.filas().stream().filter(f -> f.puesto() == 1).map(ResultadoMatriz.FilaOpcion::opcion).toList();
            resumen = "Empate en el 1º lugar: " + Textos.enumerar(empatadas) + " (" + valor.filas().getFirst().valor() + ").";
            pendientes.add(new Pendiente(TipoPendiente.REVISION, Optional.empty(), Optional.empty(), "Buscar un criterio que distinga: "
                    + Textos.enumerar(empatadas)));
        }
        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        for (String o : opciones) {
            afirmaciones.add(new AfirmacionConRol(ctx.nuevoId().get(), o, TipoAfirmacion.JUICIO_DE_VALOR, RolAfirmacion.OPCION, SentidoAfirmacion.PRODUCIDA,
                    OrigenAfirmacion.USUARIO));
        }
        ResultadoMatriz conResumen = valor.conResumen(resumen);
        return new Resultado<>(VERSION_ESQUEMA, conResumen, afirmaciones, pendientes, resumen);
    }

    /** total × probabilidad (centésimas) a un decimal: 2700 → "27,0", 2340 → "23,4". */
    static String conDecimal(long centesimas) {
        return BigDecimal.valueOf(centesimas).movePointLeft(2).setScale(1, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    @Override
    public ResultadoMatriz migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }
}
