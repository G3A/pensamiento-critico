package pensamiento.web.biblioteca;

import java.util.List;
import java.util.UUID;

import pensamiento.nucleo.Documento;

/**
 * Lo que pinta la lista de la biblioteca (P13): los documentos visibles con su estado en texto, y si hay alguno en proceso
 * o vectorizándose, para que la lista se refresque sola mientras tanto.
 */
public record VistaBiblioteca(UUID usuarioId, List<Documento> documentos) {

    public VistaBiblioteca {
        documentos = List.copyOf(documentos);
    }

    public int fragmentos() {
        return documentos.stream().mapToInt(Documento::fragmentos).sum();
    }

    /** Mientras algo se indexa o se vectoriza, la lista se vuelve a pedir cada pocos segundos. */
    public boolean enProceso() {
        return documentos.stream().anyMatch(d -> d.estado() == Documento.Estado.EN_PROCESO
                || d.estado() == Documento.Estado.INDEXADO && d.conVector() < d.fragmentos());
    }

    public boolean propio(Documento d) {
        return d.esDe(usuarioId);
    }

    /** El estado con texto (además del color): "indexando", "vectorizando 63%", "indexado", "error". */
    public static String estado(Documento d) {
        return switch (d.estado()) {
            case EN_PROCESO -> "indexando";
            case ERROR -> "error";
            case INDEXADO -> d.conVector() < d.fragmentos() ? "vectorizando " + d.porcentajeVectorizado() + "%" : "indexado";
        };
    }

    public static String claseEstado(Documento d) {
        return switch (d.estado()) {
            case EN_PROCESO -> "chip chip-pendiente";
            case ERROR -> "chip chip-error";
            case INDEXADO -> d.conVector() < d.fragmentos() ? "chip chip-pendiente" : "chip chip-ok";
        };
    }

    /** "PDF · 3 páginas · 3 fragmentos", "CSV · 6 filas como fragmentos citables". */
    public static String detalle(Documento d) {
        String tipo = switch (d.tipo()) {
            case PDF -> "PDF";
            case MARKDOWN -> "Markdown";
            case TEXTO -> "texto";
            case CSV -> "CSV";
        };
        StringBuilder sb = new StringBuilder(tipo);
        d.paginas().ifPresent(p -> sb.append(" · ").append(p).append(p == 1 ? " página" : " páginas"));
        if (d.estado() == Documento.Estado.INDEXADO) {
            if (d.tipo() == Documento.Tipo.CSV) {
                sb.append(" · ").append(d.fragmentos()).append(d.fragmentos() == 1 ? " fila como fragmento citable" : " filas como fragmentos citables");
            } else {
                sb.append(" · ").append(d.fragmentos()).append(d.fragmentos() == 1 ? " fragmento" : " fragmentos");
            }
        }
        return sb.toString();
    }
}
