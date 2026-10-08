package pensamiento.tecnicas.f1;

import java.util.List;
import java.util.UUID;

import pensamiento.nucleo.EstandarPrueba;

/**
 * Valor que pinta el patrón V01 (grafo de nodos) para T01 · Mapeo de argumentos y T06 · Reconstrucción de
 * premisas ocultas. Cada nodo apunta a su afirmación por identificador; el texto se conserva para pintar el
 * mapa sin otra consulta. Las reglas de cálculo están en docs/ejemplos/T01.md.
 *
 * @param argdown     el texto del mapa en forma canónica, exportable
 * @param conclusiones aceptabilidad de cada conclusión bajo el estándar (R04)
 * @param objecionesSinResponder códigos de los nodos con rol objeción que nadie ataca
 */
public record ResultadoMapa(
        String argdown,
        Direccion direccion,
        boolean coloresPorRol,
        boolean mostrarPesos,
        EstandarPrueba estandar,
        List<Nodo> nodos,
        List<ArgumentoMapa> argumentos,
        List<Aceptabilidad> conclusiones,
        int apoyos,
        int ataques,
        List<String> objecionesSinResponder,
        String resumen) {

    public enum Direccion {
        ARRIBA_ABAJO, IZQUIERDA_DERECHA;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** La clase CSS del nodo en el SVG: conclusion, premisa, objecion u oculta. */
    public enum Rol {
        CONCLUSION, PREMISA, OBJECION, OCULTA;

        @Override
        public String toString() {
            return name().toLowerCase();
        }

        public String nombre() {
            return switch (this) {
                case CONCLUSION -> "conclusión";
                case PREMISA -> "premisa";
                case OBJECION -> "objeción";
                case OCULTA -> "premisa oculta";
            };
        }
    }

    public enum Sentido {
        PRO, CONTRA;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /**
     * @param codigo   N1, N2… en el orden en que se definen en el texto
     * @param titulo   el título Argdown, si tiene
     * @param asumible marcado #asumible u #oculta: un supuesto para R04
     */
    public record Nodo(String codigo, UUID afirmacionId, String titulo, String texto, Rol rol, boolean asumible) {
    }

    /**
     * @param codigo  A1, A2… en el orden del texto
     * @param titulo  el nombre del argumento, si se escribió con nombre
     * @param premisas códigos de los nodos premisa, en orden
     * @param motivo  por qué es o no es aplicable, en una frase
     */
    public record ArgumentoMapa(String codigo, UUID argumentoId, String titulo, Sentido sentido, int peso, String conclusion,
                                List<String> premisas, boolean aplicable, String motivo) {
        public ArgumentoMapa {
            premisas = List.copyOf(premisas);
        }
    }

    /** @param conclusion código del nodo conclusión */
    public record Aceptabilidad(String conclusion, boolean aceptable, String frase) {
    }

    public ResultadoMapa {
        nodos = List.copyOf(nodos);
        argumentos = List.copyOf(argumentos);
        conclusiones = List.copyOf(conclusiones);
        objecionesSinResponder = List.copyOf(objecionesSinResponder);
    }

    public Nodo nodo(String codigo) {
        return nodos.stream().filter(n -> n.codigo().equals(codigo)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No hay nodo " + codigo));
    }

    /** El mismo valor con otro resumen: T06 usa el mapa de T01 con su propia línea. */
    public ResultadoMapa conResumen(String otro) {
        return new ResultadoMapa(argdown, direccion, coloresPorRol, mostrarPesos, estandar, nodos, argumentos, conclusiones, apoyos, ataques,
                objecionesSinResponder, otro);
    }
}
