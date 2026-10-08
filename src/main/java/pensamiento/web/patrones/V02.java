package pensamiento.web.patrones;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.Propuesta;

/**
 * Patrón V02, lista de verificación con estado (T02 · Modelo de Toulmin, T04 · Elementos y estándares de Paul-Elder,
 * T16 · Lista de verificación antes de decidir). Record tipado de tag/v/v02.jte: raíz id="res-{idEjecucion}" y
 * data-patron="V02". Cada ítem dice su estado con texto y, si no está completo, la pregunta que lo completaría. Todo
 * completo no dice "válido": dice que están las partes (corrección 13).
 *
 * @param medidores barras con su etiqueta visible ("Completitud 3 de 6")
 * @param secciones grupos de ítems, cada uno con título opcional (T04: elementos y estándares)
 * @param avisos    líneas destacadas debajo de la lista (T16: guardado bloqueado, firma)
 */
public record V02(Optional<UUID> idEjecucion, String sufijo, Modo modo, String titulo, String enunciado, List<Medidor> medidores,
                  List<Seccion> secciones, List<Aviso> avisos, List<Propuesta> propuestas, String resumen, String tarjeta) {

    public static final String PATRON = "V02";

    public record Medidor(String clave, String etiqueta, int valor, int total) {
    }

    public record Seccion(String titulo, List<Item> items) {
        public Seccion {
            items = List.copyOf(items);
        }
    }

    /**
     * @param estado    el estado con texto ("completa", "falta (obligatorio)")
     * @param texto     lo que escribió la persona; nulo si no hay
     * @param detalle   un complemento ("Fuente: …", "Respuesta: …", el puntaje); vacío si no hay
     * @param falta     la pregunta que lo completaría; nula si está completo
     */
    public record Item(String clave, String nombre, String estado, String claseChip, String claseItem, String texto, String detalle, String falta) {
    }

    /** @param clase "aviso" para lo que impide algo (guardado bloqueado) o "nota" para lo informativo */
    public record Aviso(String texto, String clase) {
    }

    public V02 {
        medidores = medidores == null ? List.of() : List.copyOf(medidores);
        secciones = List.copyOf(secciones);
        avisos = avisos == null ? List.of() : List.copyOf(avisos);
        propuestas = propuestas == null ? List.of() : List.copyOf(propuestas);
    }

    public String idRaiz() {
        return "res-" + idEjecucion.map(UUID::toString).orElse(PATRON + "-" + sufijo);
    }

    public String idMedidor(Medidor m) {
        return idRaiz() + "-" + m.clave();
    }

    public boolean lectura() {
        return modo == Modo.LECTURA;
    }
}
