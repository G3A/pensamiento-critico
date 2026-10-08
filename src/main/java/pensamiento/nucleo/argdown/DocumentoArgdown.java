package pensamiento.nucleo.argdown;

import java.util.List;

/**
 * Árbol de un texto en el subconjunto Argdown (docs/argdown-subconjunto.md). Cada raíz es un enunciado de primer
 * nivel, es decir, una conclusión; de él cuelgan sus relaciones de apoyo y ataque.
 */
public record DocumentoArgdown(List<Enunciado> raices) {

    public DocumentoArgdown {
        raices = List.copyOf(raices);
    }

    /** Lo que puede ir a la derecha de un + o un -: un enunciado o un argumento con nombre. */
    public sealed interface Elemento permits Enunciado, ArgumentoArgdown {
    }

    /** Las dos marcas del subconjunto. Una oculta es también asumible. */
    public enum Marca {
        OCULTA, ASUMIBLE;

        public String etiqueta() {
            return "#" + name().toLowerCase();
        }
    }

    /**
     * Un enunciado. Sin texto es una referencia a otro enunciado con el mismo título, definido en otra línea.
     *
     * @param titulo     nulo si el enunciado no tiene título
     * @param texto      nulo si es una referencia
     * @param marca      nula si no lleva marca
     * @param relaciones apoyos y ataques que recibe, en el orden del texto
     */
    public record Enunciado(String titulo, String texto, Marca marca, List<Relacion> relaciones) implements Elemento {
        public Enunciado {
            relaciones = List.copyOf(relaciones);
            if (titulo == null && texto == null) {
                throw new IllegalArgumentException("Un enunciado necesita título o texto");
            }
            if (texto == null && marca != null) {
                throw new IllegalArgumentException("Una referencia no lleva marca: la marca va en la definición");
            }
        }

        public boolean esReferencia() {
            return texto == null;
        }
    }

    /**
     * Un argumento con nombre: sus premisas son todas juntas la razón para lo que apoya o ataca.
     *
     * @param texto    descripción opcional; nula si no tiene
     * @param premisas solo apoyos a enunciados
     */
    public record ArgumentoArgdown(String titulo, String texto, List<Relacion> premisas) implements Elemento {
        public ArgumentoArgdown {
            premisas = List.copyOf(premisas);
            if (titulo == null || titulo.isBlank()) {
                throw new IllegalArgumentException("Un argumento necesita título");
            }
            if (premisas.isEmpty()) {
                throw new IllegalArgumentException("Un argumento necesita al menos una premisa");
            }
            for (Relacion r : premisas) {
                if (r.tipo() != Relacion.Tipo.APOYO || !(r.destino() instanceof Enunciado) || r.peso() != null) {
                    throw new IllegalArgumentException("Las premisas de un argumento son apoyos a enunciados, sin peso");
                }
            }
        }
    }

    /**
     * Una línea + o -.
     *
     * @param peso nulo si no se escribió; entonces el peso es 1
     */
    public record Relacion(Tipo tipo, Integer peso, Elemento destino) {

        public enum Tipo { APOYO, ATAQUE }

        public Relacion {
            if (peso != null && (peso < 0 || peso > MAXIMO_PESO)) {
                throw new IllegalArgumentException("El peso va de 0 a " + MAXIMO_PESO);
            }
        }

        public int pesoEfectivo() {
            return peso == null ? 1 : peso;
        }
    }

    public static final int MAXIMO_PESO = 9;
}
