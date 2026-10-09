package pensamiento.nucleo.puertos;

import java.util.UUID;

import pensamiento.nucleo.Documento;

/**
 * Importar un archivo a la biblioteca (P13, RNF-08): revisa tamaño y tipo por contenido, guarda el documento privado en
 * proceso, encola su indexado y lo deja en la auditoría. Corre dentro de la transacción de quien importa.
 */
public interface ImportadorDocumentos {

    /** Lo que pasó al importar: el documento en proceso o el motivo del rechazo, en español. */
    sealed interface Importacion permits Importado, Rechazado {
    }

    record Importado(Documento documento) implements Importacion {
    }

    record Rechazado(String motivo) implements Importacion {
    }

    /** 50 MB (RNF-08). */
    long TAMANO_MAXIMO = 50L * 1024 * 1024;

    Importacion importar(UUID usuarioId, UUID institucionId, String nombre, byte[] contenido);
}
