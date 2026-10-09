package pensamiento.tecnicas.f2;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import pensamiento.catalogo.BancoSocratico;
import pensamiento.tecnicas.comun.Textos;
import pensamiento.tecnicas.f1.EjecutorPaulElder.Elemento;

/**
 * La estrategia de T08 · Preguntas socráticas, en código (sección 4: "el código elige el tipo socrático y el elemento de
 * Paul-Elder; el modelo solo redacta"). Dadas la postura y las respuestas de la persona, recorre los turnos y dice qué
 * toca preguntar en cada uno: el tipo, el elemento, la rama del banco, la pregunta del banco y por qué. Es determinista:
 * con las mismas respuestas da los mismos turnos, así que el Consejero no guarda la estrategia, solo lo que pasó. Las
 * reglas están en docs/ejemplos/T08.md.
 */
public final class EstrategiaSocratica {

    /** Los seis tipos de Paul (1993), con los identificadores del banco. */
    public enum TipoSocratico {
        CLARIFICACION, SUPUESTOS, EVIDENCIA, PUNTOS_DE_VISTA, IMPLICACIONES, PREGUNTA_SOBRE_LA_PREGUNTA;

        @Override
        public String toString() {
            return name().toLowerCase();
        }

        public static TipoSocratico de(String id) {
            return valueOf(id.toUpperCase());
        }
    }

    public enum ModoSesion {
        ENSAYO("ensayo"), DECISION("decisión");

        private final String nombre;

        ModoSesion(String nombre) {
            this.nombre = nombre;
        }

        public String nombre() {
            return nombre;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public enum Orden {
        FIJO, ADAPTATIVO;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /**
     * Lo que el motor eligió para un turno.
     *
     * @param rama     la rama del banco ("general", "cifra", "difuso"…)
     * @param marca    lo que se encontró en el texto de referencia para esa rama; nulo en la general
     * @param pregunta la pregunta del banco, con la marca ya puesta
     * @param porque   por qué el motor eligió ese elemento, en una línea
     */
    public record Movimiento(int numero, TipoSocratico tipo, Elemento elemento, String rama, String marca, String pregunta, String porque) {
    }

    /** Un turno ya respondido. */
    public record Paso(Movimiento movimiento, String respuesta) {
    }

    /**
     * El recorrido de una sesión: los turnos respondidos y lo que sigue, que es otro turno o el cierre.
     *
     * @param siguiente el próximo turno si no toca el cierre
     * @param cierre    la pregunta de cierre, con la postura puesta
     */
    public record Recorrido(List<Paso> pasos, Optional<Movimiento> siguiente, boolean tocaCierre, String cierre) {
        public Recorrido {
            pasos = List.copyOf(pasos);
        }
    }

    /** La configuración que mueve la estrategia (la de T08). */
    public record Parametros(Set<TipoSocratico> tipos, Orden orden, int turnosMaximos, ModoSesion modo) {
        public Parametros {
            tipos = Set.copyOf(tipos);
        }
    }

    private final BancoSocratico banco;

    public EstrategiaSocratica(BancoSocratico banco) {
        this.banco = banco;
    }

    public static EstrategiaSocratica delCatalogo() {
        return new EstrategiaSocratica(BancoSocratico.delCatalogo());
    }

    public BancoSocratico banco() {
        return banco;
    }

    /** El tipo socrático de un elemento, según el banco. */
    public TipoSocratico tipoDe(Elemento e) {
        return TipoSocratico.de(banco.elemento(e.toString()).tipo());
    }

    public String nombreDe(TipoSocratico t) {
        return banco.tipo(t.toString()).nombre();
    }

    /**
     * Recorre la sesión.
     *
     * @param cierrePedido si la persona ya respondió (o pidió) el cierre antes de tiempo
     */
    public Recorrido recorrer(Parametros p, String postura, List<String> respuestas, boolean cierrePedido) {
        List<Paso> pasos = new ArrayList<>();
        Set<Elemento> preguntados = new LinkedHashSet<>();
        TipoSocratico anterior = null;
        String referencia = postura;
        for (int i = 0; i < respuestas.size(); i++) {
            Optional<Movimiento> m = elegir(p, i + 1, referencia, preguntados, anterior);
            if (m.isEmpty()) {
                break;
            }
            pasos.add(new Paso(m.get(), respuestas.get(i)));
            preguntados.add(m.get().elemento());
            anterior = m.get().tipo();
            referencia = respuestas.get(i);
        }
        String cierre = banco.cierre().replace("{{postura}}", Textos.comoClausula(postura));
        boolean tocaCierre = cierrePedido || pasos.size() >= p.turnosMaximos() || pendientes(p, preguntados).isEmpty();
        Optional<Movimiento> siguiente = tocaCierre ? Optional.empty() : elegir(p, pasos.size() + 1, referencia, preguntados, anterior);
        return new Recorrido(pasos, siguiente, siguiente.isEmpty(), cierre);
    }

    private List<Elemento> pendientes(Parametros p, Set<Elemento> preguntados) {
        List<Elemento> pendientes = new ArrayList<>();
        for (String id : banco.orden(p.modo().toString())) {
            Elemento e = elemento(id);
            if (p.tipos().contains(tipoDe(e)) && !preguntados.contains(e)) {
                pendientes.add(e);
            }
        }
        return pendientes;
    }

    private Optional<Movimiento> elegir(Parametros p, int numero, String referencia, Set<Elemento> preguntados, TipoSocratico anterior) {
        if (numero > p.turnosMaximos()) {
            return Optional.empty();
        }
        List<Elemento> pendientes = pendientes(p, preguntados);
        if (pendientes.isEmpty()) {
            return Optional.empty();
        }
        Elemento candidato = pendientes.getFirst();
        String porque = "Siguiente elemento pendiente en el orden del modo " + p.modo().nombre() + ".";
        if (p.orden() == Orden.ADAPTATIVO) {
            Optional<String> difuso = Marcas.primera(referencia, banco.marcas("difuso"));
            Optional<String> absoluta = Marcas.primera(referencia, banco.marcas("absoluta"));
            if (difuso.isPresent() && pendientes.contains(Elemento.CONCEPTOS)) {
                candidato = Elemento.CONCEPTOS;
                porque = "Adaptativo: «" + difuso.get() + "» es un término difuso y los conceptos están pendientes.";
            } else if (absoluta.isPresent() && pendientes.contains(Elemento.SUPUESTOS)) {
                candidato = Elemento.SUPUESTOS;
                porque = "Adaptativo: «" + absoluta.get() + "» es una afirmación absoluta y los supuestos están pendientes.";
            }
        }
        if (anterior != null && tipoDe(candidato) == anterior) {
            Optional<Elemento> otro = pendientes.stream().filter(e -> tipoDe(e) != anterior).findFirst();
            if (otro.isPresent()) {
                porque = "El siguiente pendiente era " + nombreMinuscula(candidato) + " (" + nombreDe(anterior) + "), el mismo tipo del turno anterior: pasa a "
                        + nombreMinuscula(otro.get()) + ".";
                candidato = otro.get();
            }
        }
        Rama rama = rama(candidato, referencia);
        return Optional.of(new Movimiento(numero, tipoDe(candidato), candidato, rama.rama(), rama.marca(), rama.pregunta(), porque));
    }

    private record Rama(String rama, String marca, String pregunta) {
    }

    /** La primera rama del elemento cuya marca aparece en el texto de referencia; si ninguna, la general. */
    private Rama rama(Elemento e, String referencia) {
        for (BancoSocratico.Pregunta q : banco.preguntas(e.toString())) {
            Optional<String> marca = switch (q.rama()) {
                case "general" -> Optional.of("");
                case "cifra" -> Marcas.cifra(referencia);
                case "causa" -> Marcas.causa(referencia, banco.marcas("causa"));
                default -> Marcas.primera(referencia, banco.marcas(q.rama()));
            };
            if (marca.isPresent()) {
                boolean general = "general".equals(q.rama());
                return new Rama(q.rama(), general ? null : marca.get(), general ? q.texto() : q.texto().replace("{{marca}}", marca.get()));
            }
        }
        throw new IllegalStateException("El elemento " + e + " no tiene rama general en el banco");
    }

    private static String nombreMinuscula(Elemento e) {
        return e.nombre().toLowerCase();
    }

    private static Elemento elemento(String id) {
        for (Elemento e : Elemento.values()) {
            if (e.toString().equals(id)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Elemento desconocido en el banco: " + id);
    }
}
