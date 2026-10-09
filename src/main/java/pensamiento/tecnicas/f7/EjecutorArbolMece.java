package pensamiento.tecnicas.f7;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.nucleo.Validacion;
import pensamiento.tecnicas.comun.Textos;

/**
 * T42 · Árbol de hipótesis MECE (Minto 1987). Arma el árbol desde filas con padre, revisa lo que se puede revisar sin
 * saber del tema (ramas vacías o con hojas de menos, y hermanos que comparten más de la mitad de sus palabras) y deja
 * pendientes para corregirlo. No usa IA. Las reglas están en docs/ejemplos/T42.md.
 */
@Component
public class EjecutorArbolMece implements Ejecutor<EjecutorArbolMece.Config, EjecutorArbolMece.Entrada, ResultadoArbolMece> {

    public static final IdTecnica ID = IdTecnica.de("T42");
    public static final int VERSION_ESQUEMA = 1;
    static final int TOPE_NODOS = 20;

    /** Palabras que no cuentan para el solape (docs/ejemplos/T42.md, regla 5). */
    static final Set<String> PALABRAS_VACIAS = Set.of("el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "al", "a", "en", "y",
            "o", "que", "por", "para", "con", "se", "su", "sus", "lo", "es", "no");

    /** Configuración de T42, versión de esquema 1. */
    public record Config(int profundidadMaxima, int hojasMinimas, boolean verificacionMece) {
    }

    /** @param padre el código del nodo padre (N1, N2…), escrito antes; vacío si cuelga de la raíz */
    public record Nodo(String texto, String padre) {
    }

    public record Entrada(String raiz, List<Nodo> nodos) {
        public Entrada {
            nodos = nodos == null ? List.of() : List.copyOf(nodos);
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
    public Tipos<Config, Entrada, ResultadoArbolMece> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoArbolMece.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.profundidadMaxima() < 1 || config.profundidadMaxima() > 4) {
            errores.add(new Validacion.Error("config.profundidadMaxima", "La profundidad máxima va de 1 a 4."));
        }
        if (config.hojasMinimas() < 0 || config.hojasMinimas() > 5) {
            errores.add(new Validacion.Error("config.hojasMinimas", "Las hojas mínimas por rama van de 0 a 5."));
        }
        if (Textos.vacio(entrada.raiz())) {
            errores.add(new Validacion.Error("raiz", "Escribe el problema o la pregunta de la raíz."));
        }
        if (entrada.nodos().isEmpty()) {
            errores.add(new Validacion.Error("nodos", "Escribe al menos un nodo."));
        } else if (entrada.nodos().size() > TOPE_NODOS) {
            errores.add(new Validacion.Error("nodos", "Caben como máximo " + TOPE_NODOS + " nodos."));
        }
        int[] profundidad = new int[entrada.nodos().size()];
        for (int i = 0; i < entrada.nodos().size(); i++) {
            Nodo n = entrada.nodos().get(i);
            String campo = "nodos[" + i + "]";
            if (Textos.vacio(n.texto())) {
                errores.add(new Validacion.Error(campo + ".texto", "Escribe el nodo N" + (i + 1) + "."));
            }
            Optional<Integer> padre = indicePadre(n.padre());
            if (padre.isEmpty() && !Textos.vacio(n.padre())) {
                errores.add(new Validacion.Error(campo + ".padre", "El padre de N" + (i + 1) + " se escribe con su código, por ejemplo N1."));
                continue;
            }
            if (padre.isPresent() && padre.get() >= i) {
                errores.add(new Validacion.Error(campo + ".padre", "El padre de N" + (i + 1) + " tiene que ser un nodo escrito antes."));
                continue;
            }
            profundidad[i] = padre.map(p -> profundidad[p] + 1).orElse(1);
            if (profundidad[i] > config.profundidadMaxima()) {
                errores.add(new Validacion.Error(campo + ".padre", "N" + (i + 1) + " pasa la profundidad máxima de " + config.profundidadMaxima() + "."));
            }
        }
        return new Validacion(errores);
    }

    /** "N3" o "n3" → 2; vacío si está vacío o no es un código. */
    static Optional<Integer> indicePadre(String padre) {
        if (Textos.vacio(padre)) {
            return Optional.empty();
        }
        String p = padre.strip().toUpperCase(Locale.ROOT);
        if (!p.matches("N[1-9][0-9]?")) {
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(p.substring(1)) - 1);
    }

    @Override
    public Resultado<ResultadoArbolMece> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        List<Nodo> nodos = entrada.nodos();
        int total = nodos.size();
        int[] padre = new int[total];
        int[] profundidad = new int[total];
        Map<Integer, List<Integer>> hijos = new HashMap<>();
        for (int i = 0; i < total; i++) {
            padre[i] = indicePadre(nodos.get(i).padre()).orElse(-1);
            profundidad[i] = padre[i] < 0 ? 1 : profundidad[padre[i]] + 1;
            hijos.computeIfAbsent(padre[i], k -> new ArrayList<>()).add(i);
        }
        boolean[] solape = new boolean[total];
        List<ResultadoArbolMece.Solape> solapes = new ArrayList<>();
        if (config.verificacionMece()) {
            for (List<Integer> hermanos : hijos.values()) {
                for (int a = 0; a < hermanos.size(); a++) {
                    for (int b = a + 1; b < hermanos.size(); b++) {
                        int x = hermanos.get(a);
                        int y = hermanos.get(b);
                        if (seSolapan(nodos.get(x).texto(), nodos.get(y).texto())) {
                            solape[x] = true;
                            solape[y] = true;
                            solapes.add(new ResultadoArbolMece.Solape("N" + (x + 1), "N" + (y + 1), nodos.get(x).texto().strip(), nodos.get(y).texto().strip()));
                        }
                    }
                }
            }
            solapes.sort(java.util.Comparator.comparingInt((ResultadoArbolMece.Solape s) -> Integer.parseInt(s.a().substring(1)))
                    .thenComparingInt(s -> Integer.parseInt(s.b().substring(1))));
        }
        UUID raizId = ctx.nuevoId().get();
        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        afirmaciones.add(new AfirmacionConRol(raizId, entrada.raiz().strip(), TipoAfirmacion.HECHO, RolAfirmacion.POSTURA, SentidoAfirmacion.PRODUCIDA,
                OrigenAfirmacion.USUARIO));
        List<ResultadoArbolMece.NodoArbol> resultado = new ArrayList<>();
        List<String> hojas = new ArrayList<>();
        List<String> huecos = new ArrayList<>();
        List<Pendiente> pendientes = new ArrayList<>();
        int ramas = 0;
        int vacias = 0;
        int cortas = 0;
        for (int i = 0; i < total; i++) {
            UUID id = ctx.nuevoId().get();
            String texto = nodos.get(i).texto().strip();
            afirmaciones.add(new AfirmacionConRol(id, texto, TipoAfirmacion.CAUSAL, RolAfirmacion.HIPOTESIS, SentidoAfirmacion.PRODUCIDA,
                    OrigenAfirmacion.USUARIO));
            boolean tieneHijos = hijos.containsKey(i);
            ResultadoArbolMece.Clase clase;
            if (profundidad[i] == 1) {
                ramas++;
                if (!tieneHijos && config.verificacionMece()) {
                    clase = ResultadoArbolMece.Clase.RAMA_VACIA;
                } else {
                    clase = ResultadoArbolMece.Clase.RAMA;
                }
            } else {
                clase = tieneHijos ? ResultadoArbolMece.Clase.INTERMEDIO : ResultadoArbolMece.Clase.HOJA;
            }
            if (clase == ResultadoArbolMece.Clase.HOJA) {
                hojas.add(texto);
            }
            resultado.add(new ResultadoArbolMece.NodoArbol(id, "N" + (i + 1), texto, padre[i] < 0 ? null : "N" + (padre[i] + 1), profundidad[i],
                    clase, solape[i]));
        }
        if (config.verificacionMece()) {
            for (int i = 0; i < total; i++) {
                if (profundidad[i] != 1) {
                    continue;
                }
                String texto = nodos.get(i).texto().strip();
                int deLaRama = hojasDe(i, hijos);
                if (!hijos.containsKey(i)) {
                    vacias++;
                    huecos.add(texto + ": rama vacía.");
                    pendientes.add(new Pendiente(TipoPendiente.REVISION, Optional.of(resultado.get(i).afirmacionId()), Optional.empty(),
                            "Completar la rama vacía: " + texto));
                } else if (deLaRama < config.hojasMinimas()) {
                    int faltan = config.hojasMinimas() - deLaRama;
                    cortas++;
                    huecos.add(texto + ": " + (faltan == 1 ? "le falta 1 hoja." : "le faltan " + faltan + " hojas."));
                    pendientes.add(new Pendiente(TipoPendiente.REVISION, Optional.of(resultado.get(i).afirmacionId()), Optional.empty(),
                            "Agregar hojas a: " + texto));
                }
            }
            for (ResultadoArbolMece.Solape s : solapes) {
                pendientes.add(new Pendiente(TipoPendiente.REVISION, Optional.empty(), Optional.empty(),
                        "Separar «" + s.textoA() + "» y «" + s.textoB() + "»: se solapan"));
            }
        }
        String resumen = Textos.contar(ramas, "rama", "ramas") + ", " + Textos.contar(hojas.size(), "hoja", "hojas") + " · ";
        if (!config.verificacionMece()) {
            resumen += "sin verificación MECE.";
        } else {
            resumen += (solapes.isEmpty() ? "sin solapes" : Textos.contar(solapes.size(), "solape", "solapes")) + " · ";
            List<String> partes = new ArrayList<>();
            if (vacias > 0) {
                partes.add(Textos.contar(vacias, "rama vacía", "ramas vacías"));
            }
            if (cortas > 0) {
                partes.add(Textos.contar(cortas, "rama con hojas de menos", "ramas con hojas de menos"));
            }
            resumen += (partes.isEmpty() ? "sin huecos" : String.join(", ", partes)) + ".";
        }
        ResultadoArbolMece valor = new ResultadoArbolMece(raizId, entrada.raiz().strip(), resultado, hojas, huecos, solapes, config.verificacionMece(),
                resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen);
    }

    private static int hojasDe(int nodo, Map<Integer, List<Integer>> hijos) {
        List<Integer> suyos = hijos.get(nodo);
        if (suyos == null) {
            return 0;
        }
        int cuenta = 0;
        for (int h : suyos) {
            cuenta += hijos.containsKey(h) ? hojasDe(h, hijos) : 1;
        }
        return cuenta;
    }

    /** Comparten más de la mitad de las palabras del más corto, sin tildes, sin mayúsculas y sin palabras vacías (también la usa T38). */
    public static boolean seSolapan(String a, String b) {
        Set<String> pa = palabras(a);
        Set<String> pb = palabras(b);
        int menor = Math.min(pa.size(), pb.size());
        if (menor == 0) {
            return false;
        }
        Set<String> comunes = new LinkedHashSet<>(pa);
        comunes.retainAll(pb);
        return 2 * comunes.size() > menor;
    }

    static Set<String> palabras(String texto) {
        Set<String> palabras = new LinkedHashSet<>();
        Arrays.stream(Textos.plegar(texto).split("[^\\p{L}\\p{N}]+")).filter(p -> !p.isEmpty() && !PALABRAS_VACIAS.contains(p)).forEach(palabras::add);
        return palabras;
    }

    @Override
    public ResultadoArbolMece migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }
}
