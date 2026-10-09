package pensamiento.biblioteca;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import pensamiento.nucleo.Documento;
import pensamiento.nucleo.Fragmento;
import pensamiento.nucleo.Trabajo;
import pensamiento.nucleo.puertos.Biblioteca;
import pensamiento.nucleo.puertos.ColaTrabajos;
import pensamiento.nucleo.puertos.ExtractorPdf;
import pensamiento.nucleo.puertos.ProcesadorTrabajo;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.TransaccionComoUsuario;

/**
 * El trabajo "indexar": saca el texto del documento (pdftotext para un PDF), lo trocea y guarda los fragmentos; desde ahí el
 * documento se encuentra por texto completo y se encola su vectorizado. Un PDF sin texto o ilegible queda en error con el
 * motivo. Lee y escribe como la persona dueña del documento; extraer y trocear corre fuera de toda transacción.
 */
@Component
public class IndexadorDocumentos implements ProcesadorTrabajo {

    public static final String VECTORIZAR = "vectorizar";
    static final int INTENTOS_MAXIMOS = 3;

    private record Leido(Documento documento, byte[] contenido) {
    }

    private final Biblioteca biblioteca;
    private final ExtractorPdf extractor;
    private final ColaTrabajos cola;
    private final TransaccionComoUsuario transaccion;
    private final Reloj reloj;

    public IndexadorDocumentos(Biblioteca biblioteca, ExtractorPdf extractor, ColaTrabajos cola, TransaccionComoUsuario transaccion, Reloj reloj) {
        this.biblioteca = biblioteca;
        this.extractor = extractor;
        this.cola = cola;
        this.transaccion = transaccion;
        this.reloj = reloj;
    }

    @Override
    public String tipo() {
        return ImportadorDeDocumentos.INDEXAR;
    }

    @Override
    public Desenlace procesar(Trabajo trabajo) {
        PayloadDocumento p = PayloadDocumento.de(trabajo.payload());
        try {
            Optional<Leido> leido = transaccion.ejecutar(p.usuarioId(), p.institucionId(), () -> biblioteca.porId(p.usuarioId(), p.documentoId())
                    .filter(d -> d.esDe(p.usuarioId()))
                    .flatMap(d -> biblioteca.contenido(p.usuarioId(), d.id()).map(c -> new Leido(d, c))));
            if (leido.isEmpty()) {
                return new Hecho();
            }
            List<Fragmento.Nuevo> fragmentos;
            Optional<Integer> paginas = Optional.empty();
            try {
                if (leido.get().documento().tipo() == Documento.Tipo.PDF) {
                    List<String> textos = extractor.paginas(leido.get().contenido());
                    paginas = Optional.of(textos.size());
                    List<Troceador.Pagina> porPagina = new ArrayList<>();
                    for (int i = 0; i < textos.size(); i++) {
                        porPagina.add(new Troceador.Pagina(Optional.of(i + 1), textos.get(i)));
                    }
                    fragmentos = Troceador.trocear(porPagina);
                } else {
                    String texto = DetectorTipo.texto(leido.get().contenido()).orElse("");
                    fragmentos = leido.get().documento().tipo() == Documento.Tipo.CSV
                            ? Troceador.trocearCsv(texto, DetectorTipo.separador(texto).orElse(','))
                            : Troceador.trocear(List.of(new Troceador.Pagina(Optional.empty(), texto)));
                }
            } catch (ExtractorPdf.PdfSinTexto | ExtractorPdf.PdfIlegible e) {
                return error(p, e.getMessage());
            }
            if (fragmentos.isEmpty()) {
                return error(p, "El documento no tiene texto.");
            }
            Optional<Integer> totalPaginas = paginas;
            transaccion.ejecutar(p.usuarioId(), p.institucionId(), () -> {
                biblioteca.indexar(p.usuarioId(), p.documentoId(), fragmentos, totalPaginas);
                cola.encolar(VECTORIZAR, p.comoJson(), reloj.ahora());
                return null;
            });
            return new Hecho();
        } catch (RuntimeException e) {
            if (trabajo.intentos() >= INTENTOS_MAXIMOS) {
                error(p, "No se pudo indexar el documento.");
                return new Falla("No se pudo indexar el documento: " + e.getClass().getSimpleName());
            }
            return new Reintentar(reloj.ahora().plus(Duration.ofSeconds(30)), "Falló el indexado: se reintenta en 30 s.");
        }
    }

    private Desenlace error(PayloadDocumento p, String motivo) {
        transaccion.ejecutar(p.usuarioId(), p.institucionId(), () -> {
            biblioteca.marcarError(p.usuarioId(), p.documentoId(), motivo);
            return null;
        });
        return new Hecho();
    }
}
