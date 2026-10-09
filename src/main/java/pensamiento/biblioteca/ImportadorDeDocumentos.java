package pensamiento.biblioteca;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import pensamiento.nucleo.Documento;
import pensamiento.nucleo.Uuid7;
import pensamiento.nucleo.puertos.Biblioteca;
import pensamiento.nucleo.puertos.ColaTrabajos;
import pensamiento.nucleo.puertos.ImportadorDocumentos;
import pensamiento.nucleo.puertos.RegistroAuditoria;
import pensamiento.nucleo.puertos.Reloj;

/**
 * Importa un archivo a la biblioteca (P13, RNF-08): rechaza lo vacío, lo que pasa de 50 MB y lo que no es PDF, Markdown,
 * texto ni CSV por su contenido; rechaza lo que la persona ya importó; guarda el documento privado y en proceso, encola su
 * indexado y deja la importación en la auditoría. Nada se lee del disco ni de la extensión del nombre.
 */
@Component
public class ImportadorDeDocumentos implements ImportadorDocumentos {

    public static final String INDEXAR = "indexar";

    private final Biblioteca biblioteca;
    private final ColaTrabajos cola;
    private final RegistroAuditoria auditoria;
    private final Reloj reloj;

    public ImportadorDeDocumentos(Biblioteca biblioteca, ColaTrabajos cola, RegistroAuditoria auditoria, Reloj reloj) {
        this.biblioteca = biblioteca;
        this.cola = cola;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Override
    public Importacion importar(UUID usuarioId, UUID institucionId, String nombre, byte[] contenido) {
        if (contenido.length == 0) {
            return new Rechazado("El archivo está vacío.");
        }
        if (contenido.length > TAMANO_MAXIMO) {
            return new Rechazado("El archivo pasa de 50 MB.");
        }
        Optional<Documento.Tipo> tipo = DetectorTipo.detectar(contenido);
        if (tipo.isEmpty()) {
            return new Rechazado("Tipo no permitido: solo PDF, Markdown, texto o CSV.");
        }
        String hash = sha256(contenido);
        Optional<Documento> repetido = biblioteca.propioPorHash(usuarioId, hash);
        if (repetido.isPresent()) {
            return new Rechazado("Ya importaste este documento: " + repetido.get().nombre());
        }
        UUID id = Uuid7.en(reloj.ahora());
        Documento documento = biblioteca.crear(usuarioId, institucionId, new Biblioteca.NuevoDocumento(id, nombreLimpio(nombre), tipo.get(), hash, contenido));
        cola.encolar(INDEXAR, new PayloadDocumento(usuarioId, institucionId, id).comoJson(), reloj.ahora());
        auditoria.registrar(new RegistroAuditoria.Evento(Optional.of(usuarioId), institucionId, RegistroAuditoria.Accion.IMPORTAR, "documento",
                Optional.of(id), reloj.ahora()));
        return new Importado(documento);
    }

    /** Solo el último tramo del nombre, sin controles y con 200 caracteres como máximo. */
    static String nombreLimpio(String nombre) {
        String n = nombre == null ? "" : nombre;
        n = n.substring(Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\')) + 1);
        n = n.replaceAll("\\p{Cntrl}", "").strip();
        if (n.length() > 200) {
            n = n.substring(0, 200).strip();
        }
        return n.isEmpty() ? "documento" : n;
    }

    static String sha256(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no trae SHA-256", e);
        }
    }
}
