package pensamiento.tecnicas.f7;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.Validacion;
import pensamiento.tecnicas.comun.Textos;

/**
 * T44 · SCAMPER y pensamiento lateral (de Bono 1967; Eberle 1971). Ordena las ideas por operador, señala los operadores
 * con ideas de menos y deja las seleccionadas como opciones para comparar. En el hito 4 no usa el modelo. Las reglas
 * están en docs/ejemplos/T44.md.
 */
@Component
public class EjecutorScamper implements Ejecutor<EjecutorScamper.Config, EjecutorScamper.Entrada, ResultadoScamper> {

    public static final IdTecnica ID = IdTecnica.de("T44");
    public static final int VERSION_ESQUEMA = 1;
    static final int TOPE_IDEAS = 30;

    /** Los siete operadores, en orden, con su letra. */
    public enum Operador {
        SUSTITUIR("S", "Sustituir"),
        COMBINAR("C", "Combinar"),
        ADAPTAR("A", "Adaptar"),
        MODIFICAR("M", "Modificar"),
        OTROS_USOS("P", "Poner en otros usos"),
        ELIMINAR("E", "Eliminar"),
        REORDENAR("R", "Reordenar");

        private final String letra;
        private final String nombre;

        Operador(String letra, String nombre) {
            this.letra = letra;
            this.nombre = nombre;
        }

        public String letra() {
            return letra;
        }

        public String nombre() {
            return nombre;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** Configuración de T44, versión de esquema 1. */
    public record Config(List<Operador> operadores, int ideasMinimas, int minutosPorOperador) {
        public Config {
            operadores = operadores == null ? List.of() : List.copyOf(operadores);
        }
    }

    public record Idea(String texto, Operador operador, boolean seleccionada) {
    }

    public record Entrada(String problema, List<Idea> ideas) {
        public Entrada {
            ideas = ideas == null ? List.of() : List.copyOf(ideas);
        }
    }

    @Override
    public IdTecnica id() {
        return ID;
    }

    @Override
    public int versionEsquema() {
        return VERSION_ESQUEMA;
    }

    @Override
    public Tipos<Config, Entrada, ResultadoScamper> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoScamper.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.operadores().isEmpty()) {
            errores.add(new Validacion.Error("config.operadores", "Activa al menos un operador."));
        }
        if (config.ideasMinimas() < 0 || config.ideasMinimas() > 5) {
            errores.add(new Validacion.Error("config.ideasMinimas", "Las ideas mínimas por operador van de 0 a 5."));
        }
        if (config.minutosPorOperador() < 0 || config.minutosPorOperador() > 30) {
            errores.add(new Validacion.Error("config.minutosPorOperador", "Los minutos por operador van de 0 a 30."));
        }
        if (Textos.vacio(entrada.problema())) {
            errores.add(new Validacion.Error("problema", "Escribe el problema para el que buscas opciones."));
        }
        if (entrada.ideas().isEmpty()) {
            errores.add(new Validacion.Error("ideas", "Escribe al menos una idea."));
        } else if (entrada.ideas().size() > TOPE_IDEAS) {
            errores.add(new Validacion.Error("ideas", "Caben como máximo " + TOPE_IDEAS + " ideas."));
        }
        for (int i = 0; i < entrada.ideas().size(); i++) {
            Idea idea = entrada.ideas().get(i);
            if (Textos.vacio(idea.texto())) {
                errores.add(new Validacion.Error("ideas[" + i + "].texto", "Escribe la idea " + (i + 1) + "."));
            }
            if (idea.operador() == null || !config.operadores().contains(idea.operador())) {
                errores.add(new Validacion.Error("ideas[" + i + "].operador", "Elige un operador activo para la idea " + (i + 1) + "."));
            }
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoScamper> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        List<ResultadoScamper.Celda> celdas = new ArrayList<>();
        List<String> sinIdeas = new ArrayList<>();
        List<String> pocas = new ArrayList<>();
        int conIdeas = 0;
        for (Operador op : Operador.values()) {
            if (!config.operadores().contains(op)) {
                continue;
            }
            List<ResultadoScamper.IdeaEn> suyas = entrada.ideas().stream().filter(i -> i.operador() == op)
                    .map(i -> new ResultadoScamper.IdeaEn(i.texto().strip(), i.seleccionada())).toList();
            boolean corto = suyas.size() < config.ideasMinimas();
            if (!suyas.isEmpty()) {
                conIdeas++;
            }
            if (corto && suyas.isEmpty()) {
                sinIdeas.add(op.nombre());
            } else if (corto) {
                pocas.add(op.nombre());
            }
            celdas.add(new ResultadoScamper.Celda(op.toString(), op.letra(), op.nombre(), suyas, corto));
        }
        List<String> avisos = new ArrayList<>();
        if (!sinIdeas.isEmpty()) {
            avisos.add("Sin ideas en: " + String.join(", ", sinIdeas) + ".");
        }
        if (!pocas.isEmpty()) {
            avisos.add("Menos de " + config.ideasMinimas() + " ideas en: " + String.join(", ", pocas) + ".");
        }
        List<String> seleccionadas = new ArrayList<>();
        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        for (Idea i : entrada.ideas()) {
            if (i.seleccionada()) {
                seleccionadas.add(i.texto().strip() + " (" + i.operador().letra() + ")");
                afirmaciones.add(new AfirmacionConRol(ctx.nuevoId().get(), i.texto().strip(), TipoAfirmacion.JUICIO_DE_VALOR, RolAfirmacion.OPCION,
                        SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
            }
        }
        if (seleccionadas.isEmpty()) {
            avisos.add("No elegiste ninguna idea: marca las que pasan a comparar.");
        }
        String resumen = Textos.contar(entrada.ideas().size(), "idea", "ideas") + " en " + conIdeas + " de " + celdas.size() + " operadores · "
                + (seleccionadas.isEmpty() ? "ninguna seleccionada" : Textos.contar(seleccionadas.size(), "seleccionada", "seleccionadas")) + ".";
        ResultadoScamper valor = new ResultadoScamper(entrada.problema().strip(), celdas, seleccionadas, config.minutosPorOperador(), avisos, resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, List.of(), resumen);
    }

    @Override
    public ResultadoScamper migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }
}
