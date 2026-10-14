package pensamiento.nucleo;

import java.util.List;
import java.util.Optional;

/**
 * Lo que el docente ve de su grupo (hito 10): solo agregados. Ningún campo lleva el texto de un estudiante; los únicos
 * nombres son los de quien lleva días sin practicar, con su conteo.
 *
 * @param conceptos aciertos por concepto del tema asignado; los retos de "crear" (rúbrica) van en filas aparte
 * @param bloom     cuántas personas hay en cada nivel de Bloom del tema ("sin empezar" si nunca lo practicaron)
 */
public record PanelGrupo(int miembros, int entraronHoy, int entraronEnClase, int retosHoy, int respondieronHoy, int retosDelTema,
                         List<Concepto> conceptos, List<Nivel> bloom, List<SinPracticar> sinPracticar) {

    /** Un concepto del tema: intentos y aciertos de los miembros. {@code crear} separa los retos de rúbrica. */
    public record Concepto(String concepto, boolean crear, int intentos, int aciertos, int personas) {
    }

    /** {@code nivel} es el de la escalera de Bloom o "sin empezar". */
    public record Nivel(String nivel, int personas) {
    }

    /** {@code dias} vacío: nunca practicó. */
    public record SinPracticar(String nombre, Optional<Integer> dias) {
    }

    public static PanelGrupo vacio() {
        return new PanelGrupo(0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of());
    }
}
