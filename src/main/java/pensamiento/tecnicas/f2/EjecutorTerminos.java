package pensamiento.tecnicas.f2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import pensamiento.catalogo.BancoSocratico;
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
 * T12 · Definición de términos y detección de ambigüedad (Ennis 1962; Halpern 1998). Marca en el texto los términos
 * difusos de la lista del banco y los que escribe la persona, y pide definición, ejemplo y contraejemplo de cada uno. En
 * este hito no usa el modelo. Las reglas están en docs/ejemplos/T12.md.
 */
@Component
public class EjecutorTerminos implements Ejecutor<EjecutorTerminos.Config, EjecutorTerminos.Entrada, ResultadoTerminos> {

    public static final IdTecnica ID = IdTecnica.de("T12");
    public static final int VERSION_ESQUEMA = 1;

    /** Configuración de T12, versión de esquema 1. */
    public record Config(int terminosMinimos, boolean exigirEjemplos) {
    }

    public record Termino(String termino, String definicion, String ejemplo, String contraejemplo) {
    }

    public record Entrada(String texto, List<Termino> terminos) {
        public Entrada {
            terminos = terminos == null ? List.of() : List.copyOf(terminos);
        }
    }

    private final BancoSocratico banco;

    public EjecutorTerminos() {
        this(BancoSocratico.delCatalogo());
    }

    public EjecutorTerminos(BancoSocratico banco) {
        this.banco = banco;
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
    public Tipos<Config, Entrada, ResultadoTerminos> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoTerminos.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.terminosMinimos() < 1 || config.terminosMinimos() > 5) {
            errores.add(new Validacion.Error("config.terminosMinimos", "Los términos mínimos van de 1 a 5."));
        }
        if (Textos.vacio(entrada.texto())) {
            errores.add(new Validacion.Error("texto", "Escribe el texto que quieres revisar."));
        }
        if (entrada.terminos().stream().anyMatch(t -> Textos.vacio(t.termino()))) {
            errores.add(new Validacion.Error("terminos", "Cada fila necesita su término."));
        }
        return new Validacion(errores);
    }

    private record Encontrado(String termino, int inicio, int fin, boolean deLista, Optional<Termino> escrito) {
    }

    @Override
    public Resultado<ResultadoTerminos> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        String texto = entrada.texto().strip();
        String plegado = Textos.plegar(texto);
        List<Encontrado> encontrados = new ArrayList<>();
        List<Termino> sueltosEntrada = new ArrayList<>();
        for (Termino t : entrada.terminos()) {
            Optional<int[]> pos = posicion(plegado, Textos.plegar(t.termino().strip()));
            if (pos.isPresent()) {
                agregar(encontrados, texto, pos.get(), banco.marcas("difuso").contains(Textos.plegar(t.termino().strip())), Optional.of(t));
            } else {
                sueltosEntrada.add(t);
            }
        }
        for (String difuso : banco.marcas("difuso")) {
            posicion(plegado, difuso).ifPresent(pos -> agregar(encontrados, texto, pos, true, Optional.empty()));
        }
        encontrados.sort(Comparator.comparingInt(Encontrado::inicio));

        List<ResultadoTerminos.Termino> terminos = new ArrayList<>();
        List<Pendiente> pendientes = new ArrayList<>();
        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        afirmaciones.add(new AfirmacionConRol(ctx.nuevoId().get(), texto, TipoAfirmacion.HECHO, RolAfirmacion.POSTURA, SentidoAfirmacion.PRODUCIDA,
                OrigenAfirmacion.USUARIO));
        int definidos = 0;
        for (int i = 0; i < encontrados.size(); i++) {
            Encontrado e = encontrados.get(i);
            ResultadoTerminos.Termino t = termino(config, "T" + (i + 1), e.termino(), e.inicio(), e.fin(), e.deLista() ? "lista" : "persona", e.escrito());
            terminos.add(t);
            definidos += cuenta(t, ctx, afirmaciones, pendientes);
        }
        List<ResultadoTerminos.Termino> sueltos = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        for (Termino s : sueltosEntrada) {
            ResultadoTerminos.Termino t = termino(config, "", s.termino().strip(), -1, -1, "persona", Optional.of(s));
            sueltos.add(t);
            definidos += cuenta(t, ctx, afirmaciones, pendientes);
            avisos.add("«" + t.termino() + "» no aparece en el texto.");
        }
        if (definidos < config.terminosMinimos()) {
            avisos.add("Define al menos " + Textos.contar(config.terminosMinimos(), "término", "términos") + ": llevas " + definidos + ".");
        }
        String resumen = Textos.contar(terminos.size(), "ambiguo", "ambiguos") + " · " + Textos.contar(definidos, "definido", "definidos") + ".";
        ResultadoTerminos valor = new ResultadoTerminos(texto, terminos, sueltos, terminos.size(), definidos, avisos, resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen);
    }

    /** Agrega el tramo si no se solapa con uno ya encontrado (un término escrito por la persona manda sobre la lista). */
    private static void agregar(List<Encontrado> encontrados, String texto, int[] pos, boolean deLista, Optional<Termino> escrito) {
        for (Encontrado e : encontrados) {
            if (pos[0] < e.fin() && e.inicio() < pos[1]) {
                return;
            }
        }
        encontrados.add(new Encontrado(texto.substring(pos[0], pos[1]), pos[0], pos[1], deLista, escrito));
    }

    private static Optional<int[]> posicion(String plegado, String frase) {
        Matcher m = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(frase) + "(?![\\p{L}\\p{N}])").matcher(plegado);
        return m.find() ? Optional.of(new int[] {m.start(), m.end()}) : Optional.empty();
    }

    private static ResultadoTerminos.Termino termino(Config config, String codigo, String termino, int inicio, int fin, String origen,
                                                    Optional<Termino> escrito) {
        String definicion = escrito.map(Termino::definicion).filter(d -> !Textos.vacio(d)).map(String::strip).orElse(null);
        String ejemplo = escrito.map(Termino::ejemplo).filter(d -> !Textos.vacio(d)).map(String::strip).orElse(null);
        String contraejemplo = escrito.map(Termino::contraejemplo).filter(d -> !Textos.vacio(d)).map(String::strip).orElse(null);
        String estado;
        if (definicion == null) {
            estado = "sin definir";
        } else if (config.exigirEjemplos() && (ejemplo == null || contraejemplo == null)) {
            estado = "falta ejemplo o contraejemplo";
        } else {
            estado = "definido";
        }
        return new ResultadoTerminos.Termino(codigo, termino, inicio, fin, origen, estado, definicion, ejemplo, contraejemplo);
    }

    /** 1 si el término quedó definido (con su afirmación de definición); si no, su pendiente. */
    private static int cuenta(ResultadoTerminos.Termino t, Contexto ctx, List<AfirmacionConRol> afirmaciones, List<Pendiente> pendientes) {
        switch (t.estado()) {
            case "definido" -> {
                afirmaciones.add(new AfirmacionConRol(ctx.nuevoId().get(), "«" + t.termino() + "»: " + t.definicion(), TipoAfirmacion.DEFINICION,
                        RolAfirmacion.SUPUESTO, SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
                return 1;
            }
            case "sin definir" -> pendientes.add(new Pendiente(TipoPendiente.REVISION, Optional.empty(), Optional.empty(), "Definir «" + t.termino() + "»"));
            default -> pendientes.add(new Pendiente(TipoPendiente.REVISION, Optional.empty(), Optional.empty(),
                    "Completar la definición de «" + t.termino() + "»"));
        }
        return 0;
    }

    @Override
    public ResultadoTerminos migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA + "; se pidió migrar desde la " + desdeVersion);
    }
}
