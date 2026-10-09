package pensamiento.web.verificacion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.util.MultiValueMap;

import pensamiento.flujos.FichaDeVerificacion;
import pensamiento.nucleo.Afirmacion;
import pensamiento.nucleo.puertos.Biblioteca;
import pensamiento.tecnicas.f4.EjecutorCraap;

/**
 * Lo que pinta la ficha de fuente (P12): los campos de SIFT, lectura lateral y CRAAP con sus valores y sus errores, y, si
 * viene de la biblioteca, el documento, la página y el pasaje literal. Si el modelo propuso una postura, la trae marcada como
 * propuesta para que la persona la adopte o la cambie.
 */
public final class VistaFuente {

    private final FichaDeVerificacion.Ficha ficha;
    private final Map<String, String> valores = new LinkedHashMap<>();
    private final Map<String, String> errores;
    private final EjecutorCraap.Config pesos;
    private String documento = "";
    private String pagina = "";

    private VistaFuente(FichaDeVerificacion.Ficha ficha, Map<String, String> errores, EjecutorCraap.Config pesos) {
        this.ficha = ficha;
        this.errores = Map.copyOf(errores);
        this.pesos = pesos;
    }

    public static VistaFuente nueva(FichaDeVerificacion.Ficha ficha, String fragmento, String postura, String propuesta, String porque, boolean delModelo,
                                    EjecutorCraap.Config pesos) {
        VistaFuente v = new VistaFuente(ficha, Map.of(), pesos);
        v.valores.put("fragmentoId", fragmento);
        v.valores.put("postura", postura);
        v.valores.put("propuesta", propuesta);
        v.valores.put("porque", porque);
        v.valores.put("etiquetadaPor", delModelo ? "modelo" : "usuario");
        v.valores.put("tipoFuente", "primaria");
        return v;
    }

    public static VistaFuente conErrores(FichaDeVerificacion.Ficha ficha, FichaDeVerificacion.BorradorFuente b, Map<String, String> errores,
                                         EjecutorCraap.Config pesos) {
        VistaFuente v = new VistaFuente(ficha, errores, pesos);
        v.valores.put("titulo", texto(b.titulo()));
        v.valores.put("autor", texto(b.autor()));
        v.valores.put("fecha", texto(b.fecha()));
        v.valores.put("tipoFuente", texto(b.tipoFuente()));
        v.valores.put("diseno", texto(b.diseno()));
        v.valores.put("grupo", texto(b.grupo()));
        v.valores.put("independiente", b.independiente() ? "true" : "");
        v.valores.put("original", b.original() ? "true" : "");
        v.valores.put("actualidad", numero(b.actualidad()));
        v.valores.put("relevancia", numero(b.relevancia()));
        v.valores.put("autoridad", numero(b.autoridad()));
        v.valores.put("exactitud", numero(b.exactitud()));
        v.valores.put("proposito", numero(b.proposito()));
        v.valores.put("siftFuente", texto(b.siftFuente()));
        v.valores.put("siftCobertura", texto(b.siftCobertura()));
        v.valores.put("siftContexto", texto(b.siftContexto()));
        v.valores.put("postura", texto(b.postura()));
        v.valores.put("pasaje", texto(b.pasaje()));
        v.valores.put("fragmentoId", texto(b.fragmentoId()));
        v.valores.put("etiquetadaPor", texto(b.etiquetadaPor()));
        v.valores.put("propuesta", texto(b.propuesta()));
        return v;
    }

    /** Lo que llega del formulario; un criterio de CRAAP que no es número queda fuera de rango para que la validación lo diga. */
    public static FichaDeVerificacion.BorradorFuente borrador(MultiValueMap<String, String> p) {
        return new FichaDeVerificacion.BorradorFuente(p.getFirst("titulo"), p.getFirst("autor"), p.getFirst("fecha"), p.getFirst("tipoFuente"),
                p.getFirst("diseno"), p.getFirst("grupo"), "true".equals(p.getFirst("independiente")), "true".equals(p.getFirst("original")),
                entero(p.getFirst("actualidad")), entero(p.getFirst("relevancia")), entero(p.getFirst("autoridad")), entero(p.getFirst("exactitud")),
                entero(p.getFirst("proposito")), p.getFirst("siftFuente"), p.getFirst("siftCobertura"), p.getFirst("siftContexto"), p.getFirst("postura"),
                p.getFirst("pasaje"), p.getFirst("fragmentoId"), p.getFirst("etiquetadaPor"), p.getFirst("propuesta"));
    }

    /** Con el pasaje de la biblioteca: el pasaje literal, el documento y la página. */
    public void desdeBiblioteca(Biblioteca.Cita cita) {
        valores.putIfAbsent("pasaje", cita.texto());
        if (valores.get("pasaje") == null || valores.get("pasaje").isBlank()) {
            valores.put("pasaje", cita.texto());
        }
        valores.putIfAbsent("titulo", tituloDe(cita.documento()));
        documento = cita.documento();
        pagina = cita.pagina().map(String::valueOf).orElse("");
    }

    /** "conteo-peatonal-municipio-2025.pdf" pasa a "conteo peatonal municipio 2025" como título sugerido. */
    static String tituloDe(String documento) {
        int punto = documento.lastIndexOf('.');
        String base = punto > 0 ? documento.substring(0, punto) : documento;
        return base.replace('-', ' ').replace('_', ' ').strip();
    }

    public Afirmacion afirmacion() {
        return ficha.afirmacion();
    }

    public String id() {
        return ficha.afirmacion().id().toString();
    }

    public FichaDeVerificacion.Ficha ficha() {
        return ficha;
    }

    public String valor(String campo) {
        return valores.getOrDefault(campo, "");
    }

    public boolean marcado(String campo) {
        return "true".equals(valores.get(campo));
    }

    public String error(String campo) {
        return errores.getOrDefault(campo, "");
    }

    public boolean hayErrores() {
        return !errores.isEmpty();
    }

    public boolean desdeBiblioteca() {
        return !documento.isEmpty();
    }

    public String documento() {
        return documento;
    }

    public String pagina() {
        return pagina;
    }

    public boolean propuestaDelModelo() {
        return "modelo".equals(valor("etiquetadaPor")) && !valor("propuesta").isEmpty();
    }

    /** "actualidad 1 · relevancia 1 · autoridad 2…": los pesos de CRAAP de la configuración de T21 de la persona. */
    public String pesos() {
        List<Integer> p = pesos.pesos();
        return "actualidad " + p.get(0) + " · relevancia " + p.get(1) + " · autoridad " + p.get(2) + " · exactitud " + p.get(3) + " · propósito " + p.get(4);
    }

    public List<String> criterios() {
        return List.of("actualidad", "relevancia", "autoridad", "exactitud", "proposito");
    }

    public static String nombreCriterio(String criterio) {
        return switch (criterio) {
            case "actualidad" -> "Actualidad: ¿es reciente para el tema?";
            case "relevancia" -> "Relevancia: ¿responde a tu pregunta?";
            case "autoridad" -> "Autoridad: ¿quién la firma y qué sabe del tema?";
            case "exactitud" -> "Exactitud: ¿cita datos que se pueden revisar?";
            default -> "Propósito: ¿informa, o vende y convence?";
        };
    }

    private static String texto(String t) {
        return t == null ? "" : t;
    }

    private static String numero(Integer n) {
        return n == null ? "" : String.valueOf(n);
    }

    private static Integer entero(String t) {
        if (t == null || t.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(t.strip());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
