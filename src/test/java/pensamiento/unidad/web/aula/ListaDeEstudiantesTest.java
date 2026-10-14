package pensamiento.unidad.web.aula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Test;

import pensamiento.web.aula.ListaDeEstudiantes;
import pensamiento.web.aula.ListaDeEstudiantes.Estado;
import pensamiento.web.aula.ListaDeEstudiantes.Fila;

/** El lector de la lista del alta en lote (hito 10, entregable 3): pegada o CSV, repetidas e inválidas marcadas, tope de 500. */
class ListaDeEstudiantesTest {

    @Test
    void una_lista_pegada_da_una_fila_por_linea_y_salta_las_vacias() {
        List<Fila> filas = ListaDeEstudiantes.leer("Ana María Pérez\n\n  Luis   Gómez  \r\nSofía Ruiz\n");

        assertThat(filas).containsExactly(new Fila(1, "Ana María Pérez", Estado.NUEVA), new Fila(3, "Luis Gómez", Estado.NUEVA),
                new Fila(4, "Sofía Ruiz", Estado.NUEVA));
    }

    @Test
    void las_repetidas_sin_distinguir_mayusculas_ni_tildes_se_marcan_desde_la_segunda() {
        List<Fila> filas = ListaDeEstudiantes.leer("Ana María\nANA MARIA\nana  maría\nLuis");

        assertThat(filas).extracting(Fila::estado)
                .containsExactly(Estado.NUEVA, Estado.REPETIDA_EN_LA_LISTA, Estado.REPETIDA_EN_LA_LISTA, Estado.NUEVA);
    }

    @Test
    void un_nombre_de_mas_de_60_caracteres_es_invalido() {
        String largo = "N".repeat(61);
        assertThat(ListaDeEstudiantes.leer(largo + "\nLuis")).extracting(Fila::estado).containsExactly(Estado.INVALIDA, Estado.NUEVA);
    }

    @Test
    void un_csv_toma_la_primera_columna_con_comillas_y_salta_el_encabezado() {
        String csv = "﻿nombre;documento\n\"Pérez, Ana\";1001\nLuis Gómez,1002\n\"Juan \"\"el Mono\"\" Díaz\",1003\n";

        assertThat(ListaDeEstudiantes.leer(csv)).extracting(Fila::nombre).containsExactly("Pérez, Ana", "Luis Gómez", "Juan \"el Mono\" Díaz");
    }

    @Test
    void quinientas_filas_pasan_y_quinientas_una_se_rechazan_enteras() {
        String quinientas = IntStream.rangeClosed(1, 500).mapToObj(i -> "Estudiante " + i).collect(Collectors.joining("\n"));

        assertThat(ListaDeEstudiantes.leer(quinientas)).hasSize(500);
        assertThatThrownBy(() -> ListaDeEstudiantes.leer(quinientas + "\nUna más")).isInstanceOf(ListaDeEstudiantes.Demasiadas.class)
                .hasMessage("La lista tiene 501 filas: el máximo es 500. Divídela en partes.");
    }

    @Test
    void una_lista_vacia_no_tiene_filas() {
        assertThat(ListaDeEstudiantes.leer("\n  \n")).isEmpty();
        assertThat(ListaDeEstudiantes.leer(null)).isEmpty();
    }

    @Property
    void nunca_hay_dos_filas_nuevas_con_la_misma_clave_de_nombre(@ForAll @Size(max = 40) List<@StringLength(max = 12) String> nombres) {
        List<Fila> nuevas = ListaDeEstudiantes.leer(String.join("\n", nombres)).stream().filter(f -> f.estado() == Estado.NUEVA).toList();

        assertThat(nuevas.stream().map(f -> pensamiento.nucleo.Usuario.claveDeNombre(f.nombre())).distinct().count()).isEqualTo(nuevas.size());
    }
}
