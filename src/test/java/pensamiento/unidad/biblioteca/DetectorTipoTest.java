package pensamiento.unidad.biblioteca;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import pensamiento.biblioteca.DetectorTipo;
import pensamiento.nucleo.Documento;
import pensamiento.testutil.PdfMinimo;

/** RNF-08: el tipo sale del contenido, no de la extensión; lo que no es PDF, Markdown, texto ni CSV no entra. */
class DetectorTipoTest {

    static byte[] recurso(String ruta) {
        try (InputStream in = DetectorTipoTest.class.getResourceAsStream("/" + ruta)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @ParameterizedTest(name = "{0} es {1}")
    @CsvSource({
            "biblioteca/movilidad-centro-barrio.md, MARKDOWN",
            "biblioteca/encuesta-clientes-panaderia.md, MARKDOWN",
            "biblioteca/guia-nutricion-familia.md, MARKDOWN",
            "biblioteca/boletin-policia-sector.md, MARKDOWN",
            "biblioteca/acta-junta-vecinos-marzo.txt, TEXTO",
            "biblioteca/manual-horno-panaderia.txt, TEXTO",
            "biblioteca/colegios-resultados.csv, CSV",
            "biblioteca/ventas-sucursales-2026.csv, CSV"
    })
    void los_documentos_del_banco_se_reconocen_por_su_contenido(String ruta, Documento.Tipo esperado) {
        assertThat(DetectorTipo.detectar(recurso(ruta))).contains(esperado);
    }

    @Test
    void un_pdf_es_pdf_por_sus_primeros_bytes() {
        assertThat(DetectorTipo.detectar(PdfMinimo.de(List.of("Conteo peatonal del municipio.")))).contains(Documento.Tipo.PDF);
    }

    @Test
    void una_imagen_un_zip_o_un_ejecutable_no_entran_aunque_se_llamen_pdf() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
        byte[] zip = {'P', 'K', 3, 4, 20, 0, 0, 0, 8, 0};
        byte[] ejecutable = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0};
        assertThat(DetectorTipo.detectar(png)).isEmpty();
        assertThat(DetectorTipo.detectar(zip)).isEmpty();
        assertThat(DetectorTipo.detectar(ejecutable)).isEmpty();
    }

    @Test
    void un_texto_en_latin1_que_no_es_utf8_no_entra_y_uno_vacio_tampoco() {
        assertThat(DetectorTipo.detectar("Panadería del barrio".getBytes(StandardCharsets.ISO_8859_1))).isEmpty();
        assertThat(DetectorTipo.detectar(new byte[0])).isEmpty();
        assertThat(DetectorTipo.detectar("   \n\n ".getBytes(StandardCharsets.UTF_8))).isEmpty();
    }

    @Test
    void la_prosa_con_comas_no_se_confunde_con_un_csv() {
        String prosa = "Hoy fuimos al mercado, compramos pan\nDespués, con calma, volvimos a casa\n";
        String acta = "Acta de la junta, marzo\nAsistentes: diecinueve vecinos, la presidenta.\n";
        assertThat(DetectorTipo.detectar(acta.getBytes(StandardCharsets.UTF_8))).contains(Documento.Tipo.TEXTO);
        assertThat(DetectorTipo.separador(prosa)).isEmpty();
    }

    @Test
    void un_csv_con_comillas_y_punto_y_coma_se_reconoce() {
        String csv = "mes;nota\nmayo;\"pan; integral\"\njunio;\"tortas\"\n";
        assertThat(DetectorTipo.detectar(csv.getBytes(StandardCharsets.UTF_8))).contains(Documento.Tipo.CSV);
        assertThat(DetectorTipo.separador(csv)).contains(';');
    }

    @Test
    void la_marca_de_orden_de_bytes_se_quita() {
        byte[] conBom = ("﻿# Título\n\nTexto.").getBytes(StandardCharsets.UTF_8);
        assertThat(DetectorTipo.texto(conBom)).contains("# Título\n\nTexto.");
    }
}
