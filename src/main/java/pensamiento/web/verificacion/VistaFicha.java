package pensamiento.web.verificacion;

import java.util.List;
import java.util.Optional;

import pensamiento.flujos.FichaDeVerificacion;
import pensamiento.nucleo.EstadoAfirmacion;
import pensamiento.nucleo.EstandarPrueba;
import pensamiento.nucleo.Evidencia;
import pensamiento.nucleo.EvidenciaGuardada;
import pensamiento.nucleo.FichaFuente;

/**
 * Lo que pinta la ficha de verificación (P10 y P11): la ficha del servicio más los textos y las clases de cada estado. Todo
 * estado lleva texto además del color (RNF-06).
 *
 * @param mensaje lo que acaba de pasar ("Veredicto guardado: …"); vacío si nada
 * @param clave   la clave de idempotencia del próximo veredicto (doble clic)
 * @param modelo  si el modelo local está disponible para etiquetar pasajes
 */
public record VistaFicha(FichaDeVerificacion.Ficha f, String mensaje, String clave, boolean modelo) {

    /** Una evidencia como se lee en la ficha. */
    public record EvidenciaVista(String id, String titulo, String detalle, String postura, String clasePostura, String fuerza, String ubicacion,
                                 String enlaceOriginal, String pasaje, List<String> sift) {
    }

    public String id() {
        return f.afirmacion().id().toString();
    }

    public static String estado(EstadoAfirmacion e) {
        return FichaDeVerificacion.texto(e);
    }

    public static String claseEstado(EstadoAfirmacion e) {
        return switch (e) {
            case VERIFICADA -> "chip chip-ok";
            case REFUTADA, DISPUTADA -> "chip chip-aviso";
            default -> "chip chip-pendiente";
        };
    }

    public String confianza() {
        return f.afirmacion().confianza().map(String::valueOf).orElse("");
    }

    public String neta() {
        int n = f.calculo().neta();
        return n > 0 ? "+" + n : String.valueOf(n);
    }

    public int maximoNeta() {
        return Math.max(8, Math.abs(f.calculo().neta()));
    }

    public List<EvidenciaVista> evidencias() {
        return f.evidencias().stream().map(this::vista).toList();
    }

    private EvidenciaVista vista(EvidenciaGuardada e) {
        FichaFuente fuente = e.fuente();
        StringBuilder detalle = new StringBuilder(fuente.tipo().name().toLowerCase());
        fuente.disenoEstudio().ifPresent(d -> detalle.append(" · ").append(diseno(d)));
        fuente.fecha().ifPresent(d -> detalle.append(" · ").append(d));
        fuente.autor().ifPresent(a -> detalle.append(" · ").append(a));
        fuente.grupoOrigen().ifPresent(g -> detalle.append(" · grupo ").append(g));
        detalle.append(fuente.independiente() ? " · independiente" : " · no independiente");
        detalle.append(fuente.accesoOriginal() ? " · con el original" : " · sin el original");
        fuente.puntajeCraap().ifPresent(c -> detalle.append(" · CRAAP ").append(c).append(" de 25"));
        String postura;
        String clase;
        if (!e.adoptada()) {
            postura = "propuesta del modelo · sin adoptar · no cuenta";
            clase = "chip chip-pendiente";
        } else {
            postura = e.postura().name().toLowerCase() + (e.etiquetadaPor() == Evidencia.EtiquetadaPor.MODELO ? " · etiquetada por el modelo, adoptada" : "");
            clase = switch (e.postura()) {
                case APOYA -> "chip chip-ok";
                case CONTRADICE -> "chip chip-aviso";
                case MATIZA -> "chip chip-pendiente";
            };
        }
        Integer fuerza = f.calculo().fuerzas().getOrDefault(e.id(), e.fuerza());
        String ubicacion;
        String enlace = null;
        if (fuente.documentoRetirado()) {
            ubicacion = "biblioteca: " + fuente.documentoNombre().orElse("") + " · documento retirado";
        } else if (fuente.documentoId().isPresent()) {
            ubicacion = "biblioteca: " + fuente.documentoNombre().orElse("") + fuente.pagina().map(p -> ", p. " + p).orElse("");
            enlace = "/biblioteca/" + fuente.documentoId().get() + "/original";
        } else {
            ubicacion = "registrada a mano";
        }
        List<String> sift = java.util.stream.Stream.of(
                        nota("Investigué la fuente", fuente.sift().investigue()), nota("Mejor cobertura", fuente.sift().cobertura()),
                        nota("Contexto original", fuente.sift().contexto()))
                .flatMap(Optional::stream).toList();
        return new EvidenciaVista(e.id().toString(), fuente.titulo(), detalle.toString(), postura, clase, "fuerza " + fuerza + " (R01)", ubicacion, enlace,
                e.pasaje(), sift);
    }

    private static Optional<String> nota(String nombre, String texto) {
        return texto == null || texto.isBlank() ? Optional.empty() : Optional.of(nombre + ": " + texto);
    }

    static String diseno(pensamiento.nucleo.Fuente.DisenoEstudio d) {
        return switch (d) {
            case REVISION_SISTEMATICA -> "revisión sistemática";
            case ENSAYO_CONTROLADO -> "ensayo controlado";
            case OBSERVACIONAL -> "observacional";
            case OPINION_EXPERTO -> "opinión de experto";
            case TESTIMONIO -> "testimonio";
        };
    }

    public static String si(boolean valor) {
        return valor ? "sí" : "no";
    }

    public List<EstandarPrueba> estandares() {
        return List.of(EstandarPrueba.values());
    }

    public static String claseChequeo(FichaDeVerificacion.Chequeo c) {
        return "aviso".equals(c.estado()) ? "chip chip-aviso" : "chip chip-pendiente";
    }
}
