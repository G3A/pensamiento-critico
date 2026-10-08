package pensamiento.web.tecnicas;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import pensamiento.nucleo.Tecnica;
import pensamiento.web.formulario.Campo;
import pensamiento.web.formulario.ConstructorVista;
import pensamiento.web.formulario.VistaCampo;

/**
 * El formulario de entrada de una técnica (#form-{tecnica}) listo para pintar: campos, configuración que viaja
 * oculta con él, clave de idempotencia y de dónde sale la configuración.
 *
 * @param origenConfig    "tu configuración" o "la configuración del ejemplo …"
 * @param erroresConfig   errores de la configuración que viaja oculta (no tienen campo visible en este formulario)
 */
public record VistaFormulario(
        String tecnica,
        String idFormulario,
        List<VistaCampo> campos,
        List<Oculto> config,
        String clave,
        String origenConfig,
        String resumenConfig,
        List<String> erroresConfig,
        Modelo modelo) {

    public record Oculto(String nombre, String valor) {
    }

    /**
     * El modelo local en este formulario: si la configuración lo pide, si responde ahora mismo y si la técnica quedó
     * marcada experimental en el informe de evaluación.
     */
    public record Modelo(boolean pide, boolean disponible, boolean experimental) {
        public static final Modelo NINGUNO = new Modelo(false, false, false);
    }

    public VistaFormulario conModelo(Modelo m) {
        return new VistaFormulario(tecnica, idFormulario, campos, config, clave, origenConfig, resumenConfig, erroresConfig, m);
    }

    public String idEspera() {
        return "espera-" + tecnica;
    }

    public String idPedir() {
        return "pedir-" + tecnica;
    }

    public String id() {
        return "form-" + tecnica;
    }

    public String idResultado() {
        return "resultado-" + tecnica;
    }

    public static VistaFormulario de(Tecnica t, List<Campo> camposConfig, List<Campo> camposEntrada, Map<String, Object> config,
                                     Map<String, Object> valores, Map<String, String> errores, String clave, String origenConfig) {
        String accionUrl = "/tecnicas/" + t.id() + "/formulario";
        List<VistaCampo> campos = ConstructorVista.para(t.id().valor(), camposEntrada, valores, config, errores, accionUrl)
                .construir(camposEntrada, valores, "", "");
        List<Oculto> ocultos = new ArrayList<>();
        for (Campo c : camposConfig) {
            Object v = config.get(c.nombre());
            if (v instanceof List<?> lista) {
                // Un conjunto viaja como un parámetro repetido; una lista ordenada, con su posición.
                for (int i = 0; i < lista.size(); i++) {
                    String nombre = c.tipo() == Campo.Tipo.LISTA_ORDENADA ? "config." + c.nombre() + "[" + i + "]" : "config." + c.nombre();
                    ocultos.add(new Oculto(nombre, String.valueOf(lista.get(i))));
                }
            } else {
                ocultos.add(new Oculto("config." + c.nombre(), v == null ? "" : v.toString()));
            }
        }
        List<String> erroresConfig = errores.entrySet().stream().filter(e -> e.getKey().startsWith("config.") || e.getKey().startsWith("_"))
                .map(Map.Entry::getValue).toList();
        return new VistaFormulario(t.id().valor(), "form-" + t.id(), campos, ocultos, clave, origenConfig, resumen(camposConfig, config), erroresConfig, Modelo.NINGUNO);
    }

    /** "Escala de cada celda: C, I, N · Máximo de hipótesis: 4 · Pesos de evidencia activos: sí". */
    public static String resumen(List<Campo> camposConfig, Map<String, Object> config) {
        List<String> partes = new ArrayList<>();
        for (Campo c : camposConfig) {
            Object v = config.get(c.nombre());
            String etiqueta = corta(c.etiqueta());
            String valor = switch (c.tipo()) {
                case BOOLEANO -> Boolean.TRUE.equals(v) ? "sí" : "no";
                case ENUMERACION -> c.opcionesCon(config).stream().filter(o -> o.valor().equals(String.valueOf(v)))
                        .map(o -> corta(o.etiqueta())).findFirst().orElse(String.valueOf(v));
                case CONJUNTO, LISTA_ORDENADA -> v instanceof List<?> lista ? String.join(", ", lista.stream()
                        .map(x -> c.opcionesCon(config).stream().filter(o -> o.valor().equals(String.valueOf(x))).map(o -> corta(o.etiqueta()))
                                .findFirst().orElse(String.valueOf(x))).toList()) : "";
                default -> v == null ? "" : v.toString();
            };
            partes.add(etiqueta + ": " + valor);
        }
        return String.join(" · ", partes);
    }

    /** Hasta el primer paréntesis o los primeros dos puntos: "C, I, N: consistente…" pasa a "C, I, N". */
    private static String corta(String texto) {
        int corte = texto.length();
        for (String marca : List.of(" (", ":")) {
            int i = texto.indexOf(marca);
            if (i > 0) {
                corte = Math.min(corte, i);
            }
        }
        return texto.substring(0, corte);
    }
}
