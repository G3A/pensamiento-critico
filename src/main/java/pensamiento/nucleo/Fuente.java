package pensamiento.nucleo;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Documento, persona u organización que respalda evidencias. grupoOrigen define la independencia mutua:
 * dos fuentes son independientes si su grupo de origen difiere.
 */
public record Fuente(
        UUID id,
        String titulo,
        TipoFuente tipo,
        Optional<DisenoEstudio> disenoEstudio,
        Optional<LocalDate> fecha,
        Optional<String> grupoOrigen,
        boolean independienteDelAutor,
        boolean accesoOriginal,
        Optional<Integer> puntajeCraap) {

    public enum TipoFuente { PRIMARIA, SECUNDARIA, TERCIARIA }

    public enum DisenoEstudio { REVISION_SISTEMATICA, ENSAYO_CONTROLADO, OBSERVACIONAL, OPINION_EXPERTO, TESTIMONIO }
}
