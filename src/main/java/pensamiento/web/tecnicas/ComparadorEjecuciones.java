package pensamiento.web.tecnicas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import pensamiento.nucleo.Ejecucion;
import pensamiento.web.formulario.LenguajeCampos;

/**
 * Compara dos ejecuciones de la misma técnica sobre el JSON de su configuración y de su resultado (P08). Los
 * elementos de una lista se emparejan por su texto y se marcan igual, cambió o nueva; lo que estaba en la
 * anterior y ya no está se marca "ya no está". Funciona para cualquier técnica cuyo resultado tenga listas de
 * objetos con "texto".
 */
public final class ComparadorEjecuciones {

    public enum Estado {
        IGUAL("igual"), CAMBIO("cambió"), NUEVA("nueva"), QUITADA("ya no está");

        private final String texto;

        Estado(String texto) {
            this.texto = texto;
        }

        public String texto() {
            return texto;
        }

        public String clase() {
            return "diferencia-" + name().toLowerCase();
        }
    }

    public record Diferencia(Estado estado, String texto, List<String> detalle) {
    }

    public record Seccion(String titulo, List<Diferencia> diferencias) {
    }

    public record Comparacion(Ejecucion anterior, Ejecucion posterior, List<String> cambiosDeConfiguracion, List<Seccion> secciones,
                              String resumenAnterior, String resumenPosterior) {
    }

    /** Campos que cambian entre ejecuciones sin que cambie el contenido: identificadores y códigos de posición. */
    private static final Set<String> IGNORADOS = Set.of("afirmacionId", "codigo", "evidenciasEnContra");

    private ComparadorEjecuciones() {
    }

    /** Ordena por fecha: la anterior primero. */
    public static Comparacion comparar(Ejecucion a, Ejecucion b) {
        Ejecucion anterior = a.creadaEn().isAfter(b.creadaEn()) ? b : a;
        Ejecucion posterior = anterior == a ? b : a;
        Map<String, Object> configA = LenguajeCampos.mapa(anterior.config());
        Map<String, Object> configB = LenguajeCampos.mapa(posterior.config());
        List<String> cambiosConfig = new ArrayList<>();
        for (String clave : union(configA.keySet(), configB.keySet())) {
            if (!Objects.equals(configA.get(clave), configB.get(clave))) {
                cambiosConfig.add(clave + ": " + texto(configA.get(clave)) + " → " + texto(configB.get(clave)));
            }
        }
        Map<String, Object> resA = LenguajeCampos.mapa(anterior.resultado());
        Map<String, Object> resB = LenguajeCampos.mapa(posterior.resultado());
        List<Seccion> secciones = new ArrayList<>();
        for (String clave : union(resA.keySet(), resB.keySet())) {
            if (esListaDeObjetosConTexto(resA.get(clave)) || esListaDeObjetosConTexto(resB.get(clave))) {
                secciones.add(new Seccion(clave, listas(lista(resA.get(clave)), lista(resB.get(clave)))));
            }
        }
        return new Comparacion(anterior, posterior, cambiosConfig, secciones, anterior.resumen(), posterior.resumen());
    }

    private static List<Diferencia> listas(List<Map<String, Object>> antes, List<Map<String, Object>> despues) {
        Map<String, Map<String, Object>> porTextoAntes = new LinkedHashMap<>();
        antes.forEach(m -> porTextoAntes.putIfAbsent(String.valueOf(m.get("texto")), m));
        List<Diferencia> diferencias = new ArrayList<>();
        for (Map<String, Object> d : despues) {
            String texto = String.valueOf(d.get("texto"));
            Map<String, Object> a = porTextoAntes.remove(texto);
            if (a == null) {
                diferencias.add(new Diferencia(Estado.NUEVA, texto, List.of()));
                continue;
            }
            List<String> detalle = new ArrayList<>();
            for (String campo : union(a.keySet(), d.keySet())) {
                if (!IGNORADOS.contains(campo) && !"texto".equals(campo) && !Objects.equals(a.get(campo), d.get(campo))) {
                    detalle.add(campo + ": " + texto(a.get(campo)) + " → " + texto(d.get(campo)));
                }
            }
            diferencias.add(new Diferencia(detalle.isEmpty() ? Estado.IGUAL : Estado.CAMBIO, texto, detalle));
        }
        porTextoAntes.keySet().forEach(t -> diferencias.add(new Diferencia(Estado.QUITADA, t, List.of())));
        return diferencias;
    }

    private static boolean esListaDeObjetosConTexto(Object valor) {
        return valor instanceof List<?> l && !l.isEmpty() && l.getFirst() instanceof Map<?, ?> m && m.containsKey("texto");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lista(Object valor) {
        if (!(valor instanceof List<?> l)) {
            return List.of();
        }
        return l.stream().filter(x -> x instanceof Map<?, ?>).map(x -> (Map<String, Object>) x).toList();
    }

    private static List<String> union(Set<String> a, Set<String> b) {
        List<String> claves = new ArrayList<>(a);
        b.stream().filter(k -> !a.contains(k)).forEach(claves::add);
        return claves;
    }

    private static String texto(Object valor) {
        if (valor == null) {
            return "—";
        }
        if (valor instanceof Boolean bool) {
            return bool ? "sí" : "no";
        }
        return valor.toString();
    }
}
