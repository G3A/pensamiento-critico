package pensamiento.unidad.web.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import pensamiento.web.seguridad.FiltroInstitucionDeSesion;
import pensamiento.web.seguridad.RutasReservadas;

/** Ninguna institución puede llamarse como una ruta de la app: toda ruta de un controlador está en la lista reservada. */
class RutasReservadasTest {

    @Test
    void el_primer_segmento_de_toda_ruta_de_controlador_esta_reservado() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider escaner = new ClassPathScanningCandidateComponentProvider(false);
        escaner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        Set<String> segmentos = new TreeSet<>();
        for (var candidato : escaner.findCandidateComponents("pensamiento")) {
            Class<?> clase = Class.forName(candidato.getBeanClassName());
            Stream.concat(Stream.of(clase), Arrays.stream(clase.getDeclaredMethods()).map(Method.class::cast))
                    .map(e -> e instanceof Class<?> c ? AnnotatedElementUtils.findMergedAnnotation(c, RequestMapping.class)
                            : AnnotatedElementUtils.findMergedAnnotation((Method) e, RequestMapping.class))
                    .filter(m -> m != null)
                    .flatMap(m -> Stream.concat(Arrays.stream(m.path()), Arrays.stream(m.value())))
                    .map(ruta -> ruta.startsWith("/") ? ruta.substring(1) : ruta)
                    .map(ruta -> ruta.contains("/") ? ruta.substring(0, ruta.indexOf('/')) : ruta)
                    .filter(s -> !s.isEmpty() && !s.startsWith("{"))
                    .forEach(segmentos::add);
        }
        assertThat(segmentos).as("hay controladores").isNotEmpty();
        assertThat(RutasReservadas.PRIMER_SEGMENTO).containsAll(segmentos);
    }

    @Test
    void las_carpetas_de_archivos_estaticos_estan_reservadas() throws java.io.IOException {
        try (var archivos = java.nio.file.Files.list(java.nio.file.Path.of("src/main/resources/static"))) {
            assertThat(archivos.filter(java.nio.file.Files::isDirectory).map(p -> p.getFileName().toString()).toList())
                    .isNotEmpty().allSatisfy(carpeta -> assertThat(RutasReservadas.PRIMER_SEGMENTO).contains(carpeta));
        }
    }

    @Test
    void una_ruta_de_institucion_valida_no_es_reservada_ni_tiene_tildes_ni_mayusculas() {
        assertThat(RutasReservadas.deInstitucion("colegio-norte")).contains("colegio-norte");
        assertThat(RutasReservadas.deInstitucion("catalogo")).isEmpty();
        assertThat(RutasReservadas.deInstitucion("operador")).isEmpty();
        assertThat(RutasReservadas.deInstitucion("Colegio")).isEmpty();
        assertThat(RutasReservadas.deInstitucion("colegió")).isEmpty();
        assertThat(RutasReservadas.deInstitucion("-colegio")).isEmpty();
        assertThat(RutasReservadas.deInstitucion("ab")).isEmpty();
        assertThat(RutasReservadas.deInstitucion("a".repeat(41))).isEmpty();
        assertThat(RutasReservadas.deInstitucion("app.css")).isEmpty();
    }

    @Test
    void con_sesion_en_una_institucion_la_ruta_de_otra_se_reconoce_como_ajena() {
        var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/colegio-sur");
        request.setRequestURI("/colegio-sur");
        assertThat(FiltroInstitucionDeSesion.traeOtraInstitucion(request, "colegio-norte")).isTrue();
        assertThat(FiltroInstitucionDeSesion.traeOtraInstitucion(request, "colegio-sur")).isFalse();

        var formulario = new org.springframework.mock.web.MockHttpServletRequest("POST", "/sesion");
        formulario.setRequestURI("/sesion");
        formulario.setParameter("institucion", "colegio-sur");
        assertThat(FiltroInstitucionDeSesion.traeOtraInstitucion(formulario, "colegio-norte")).isTrue();

        var propia = new org.springframework.mock.web.MockHttpServletRequest("GET", "/expedientes/123");
        propia.setRequestURI("/expedientes/123");
        assertThat(FiltroInstitucionDeSesion.traeOtraInstitucion(propia, "colegio-norte")).as("las rutas de la app no traen institución").isFalse();

        var estatico = new org.springframework.mock.web.MockHttpServletRequest("GET", "/ext/sse.js");
        estatico.setRequestURI("/ext/sse.js");
        assertThat(FiltroInstitucionDeSesion.traeOtraInstitucion(estatico, "colegio-norte")).as("un archivo estático").isFalse();

        var invitacion = new org.springframework.mock.web.MockHttpServletRequest("GET", "/colegio-sur/invitacion");
        invitacion.setRequestURI("/colegio-sur/invitacion");
        assertThat(FiltroInstitucionDeSesion.traeOtraInstitucion(invitacion, "colegio-norte")).isTrue();
    }

}
