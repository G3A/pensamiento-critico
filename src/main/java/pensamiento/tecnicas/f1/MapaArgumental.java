package pensamiento.tecnicas.f1;

import pensamiento.tecnicas.comun.Textos;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Argumento;
import pensamiento.nucleo.ArgumentoProducido;
import pensamiento.nucleo.EstadoAfirmacion;
import pensamiento.nucleo.EstandarPrueba;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.nucleo.argdown.DocumentoArgdown;
import pensamiento.nucleo.argdown.DocumentoArgdown.ArgumentoArgdown;
import pensamiento.nucleo.argdown.DocumentoArgdown.Enunciado;
import pensamiento.nucleo.argdown.DocumentoArgdown.Marca;
import pensamiento.nucleo.argdown.DocumentoArgdown.Relacion;
import pensamiento.nucleo.reglas.R04Aceptabilidad;

/**
 * Del árbol Argdown al mapa: nodos con rol, argumentos con aplicabilidad (R04), aceptabilidad de cada
 * conclusión, objeciones sin responder, pendientes, afirmaciones y argumentos para guardar. Dominio puro;
 * las reglas están escritas y calculadas a mano en docs/ejemplos/T01.md.
 */
public final class MapaArgumental {

    /** Lo que el mapa produce: el valor que pinta V01 y lo que la ejecución guarda. */
    public record Construido(ResultadoMapa valor, List<AfirmacionConRol> afirmaciones, List<Pendiente> pendientes,
                             List<ArgumentoProducido> argumentos) {
    }

    /** Opciones de presentación y el estándar de prueba (configuración de T01). */
    public record Opciones(ResultadoMapa.Direccion direccion, boolean coloresPorRol, boolean mostrarPesos, EstandarPrueba estandar) {
        public static Opciones porDefecto() {
            return new Opciones(ResultadoMapa.Direccion.ARRIBA_ABAJO, true, false, EstandarPrueba.PREPONDERANCIA);
        }
    }

    /** Nodo mientras se arma: el bando (+1 del lado de la conclusión, -1 del otro) se fija la primera vez. */
    private static final class NodoEnArmado {
        final String codigo;
        final UUID id;
        final Enunciado definicion;
        final boolean raiz;
        int bando;

        NodoEnArmado(String codigo, UUID id, Enunciado definicion, boolean raiz) {
            this.codigo = codigo;
            this.id = id;
            this.definicion = definicion;
            this.raiz = raiz;
        }
    }

    private record ArgumentoEnArmado(String codigo, UUID id, String titulo, ResultadoMapa.Sentido sentido, int peso,
                                     NodoEnArmado conclusion, List<NodoEnArmado> premisas) {
    }

    private final Map<String, NodoEnArmado> porTitulo = new HashMap<>();
    private final Map<Enunciado, NodoEnArmado> porDefinicion = new java.util.IdentityHashMap<>();
    private final List<NodoEnArmado> nodos = new ArrayList<>();
    private final List<ArgumentoEnArmado> argumentos = new ArrayList<>();
    private final Supplier<UUID> ids;

    private MapaArgumental(Supplier<UUID> ids) {
        this.ids = ids;
    }

    public static Construido construir(DocumentoArgdown documento, String argdownCanonico, Opciones opciones, Supplier<UUID> ids) {
        MapaArgumental m = new MapaArgumental(ids);
        for (Enunciado raiz : documento.raices()) {
            m.definir(raiz, true);
        }
        for (Enunciado raiz : documento.raices()) {
            NodoEnArmado nodo = m.resolver(raiz);
            if (nodo.bando == 0) {
                nodo.bando = 1;
            }
            m.recorrer(raiz, nodo);
        }
        return m.armar(argdownCanonico, opciones);
    }

    // ------------------------------------------------------------------------------------------------
    // Nodos y argumentos
    // ------------------------------------------------------------------------------------------------

    /** Primera pasada, en preorden: cada definición (enunciado con texto) es un nodo, con código y uuid. */
    private void definir(Enunciado e, boolean raiz) {
        if (!e.esReferencia()) {
            NodoEnArmado nodo = new NodoEnArmado("N" + (nodos.size() + 1), ids.get(), e, raiz);
            nodos.add(nodo);
            porDefinicion.put(e, nodo);
            if (e.titulo() != null) {
                porTitulo.put(e.titulo(), nodo);
            }
        }
        for (Relacion r : e.relaciones()) {
            if (r.destino() instanceof Enunciado d) {
                definir(d, false);
            } else {
                ((ArgumentoArgdown) r.destino()).premisas().forEach(p -> definir((Enunciado) p.destino(), false));
            }
        }
    }

    private NodoEnArmado resolver(Enunciado e) {
        return e.esReferencia() ? porTitulo.get(e.titulo()) : porDefinicion.get(e);
    }

    /** Segunda pasada, en preorden: cada línea + o - crea un argumento y fija el bando de sus premisas. */
    private void recorrer(Enunciado e, NodoEnArmado nodo) {
        for (Relacion r : e.relaciones()) {
            ResultadoMapa.Sentido sentido = r.tipo() == Relacion.Tipo.APOYO ? ResultadoMapa.Sentido.PRO : ResultadoMapa.Sentido.CONTRA;
            List<Enunciado> premisas = r.destino() instanceof Enunciado d ? List.of(d)
                    : ((ArgumentoArgdown) r.destino()).premisas().stream().map(p -> (Enunciado) p.destino()).toList();
            String titulo = r.destino() instanceof ArgumentoArgdown a ? a.titulo() : null;
            List<NodoEnArmado> resueltas = new ArrayList<>();
            for (Enunciado p : premisas) {
                NodoEnArmado premisa = resolver(p);
                if (!resueltas.contains(premisa)) {
                    resueltas.add(premisa);
                }
                if (premisa.bando == 0) {
                    premisa.bando = sentido == ResultadoMapa.Sentido.PRO ? nodo.bando : -nodo.bando;
                }
            }
            argumentos.add(new ArgumentoEnArmado("A" + (argumentos.size() + 1), ids.get(), titulo, sentido, r.pesoEfectivo(), nodo, resueltas));
            for (Enunciado p : premisas) {
                recorrer(p, resolver(p));
            }
        }
    }

    private static ResultadoMapa.Rol rol(NodoEnArmado n) {
        if (n.definicion.marca() == Marca.OCULTA) {
            return ResultadoMapa.Rol.OCULTA;
        }
        if (n.raiz) {
            return ResultadoMapa.Rol.CONCLUSION;
        }
        return n.bando >= 0 ? ResultadoMapa.Rol.PREMISA : ResultadoMapa.Rol.OBJECION;
    }

    private static boolean asumible(NodoEnArmado n) {
        return n.definicion.marca() != null;
    }

    // ------------------------------------------------------------------------------------------------
    // R04, conteos, pendientes y lo que se guarda
    // ------------------------------------------------------------------------------------------------

    private Construido armar(String argdown, Opciones opciones) {
        EstandarPrueba estandar = opciones.estandar();
        Map<UUID, R04Aceptabilidad.EstadoPremisa> estados = new HashMap<>();
        for (NodoEnArmado n : nodos) {
            // En este hito nada llega a verificada: lo asumible se juzga por la marca de la premisa (docs/ejemplos/T01.md, regla 5).
            estados.put(n.id, new R04Aceptabilidad.EstadoPremisa(EstadoAfirmacion.SIN_VERIFICAR, false, atacado(n)));
        }
        Map<ArgumentoEnArmado, Argumento> delNucleo = new LinkedHashMap<>();
        for (ArgumentoEnArmado a : argumentos) {
            List<Argumento.Premisa> premisas = new ArrayList<>();
            for (int i = 0; i < a.premisas().size(); i++) {
                NodoEnArmado p = a.premisas().get(i);
                premisas.add(new Argumento.Premisa(p.id, i + 1, asumible(p)));
            }
            delNucleo.put(a, new Argumento(a.id(), a.conclusion().id, premisas, a.peso(),
                    a.sentido() == ResultadoMapa.Sentido.PRO ? Argumento.Sentido.PRO : Argumento.Sentido.CONTRA));
        }
        List<Argumento> grafo = List.copyOf(delNucleo.values());

        List<ResultadoMapa.ArgumentoMapa> argumentosMapa = new ArrayList<>();
        for (ArgumentoEnArmado a : argumentos) {
            boolean aplicable = R04Aceptabilidad.aplicable(delNucleo.get(a), estados, estandar);
            argumentosMapa.add(new ResultadoMapa.ArgumentoMapa(a.codigo(), a.id(), a.titulo(), a.sentido(), a.peso(), a.conclusion().codigo,
                    a.premisas().stream().map(p -> p.codigo).toList(), aplicable, motivo(a, aplicable, estandar)));
        }

        List<ResultadoMapa.Aceptabilidad> conclusiones = new ArrayList<>();
        for (NodoEnArmado n : nodos) {
            if (n.raiz) {
                conclusiones.add(aceptabilidad(n, estandar, grafo, delNucleo, estados));
            }
        }

        List<String> sinResponder = nodos.stream().filter(n -> rol(n) == ResultadoMapa.Rol.OBJECION && !atacado(n)).map(n -> n.codigo).toList();
        int apoyos = (int) argumentos.stream().filter(a -> a.sentido() == ResultadoMapa.Sentido.PRO).count();
        int ataques = argumentos.size() - apoyos;

        List<Pendiente> pendientes = new ArrayList<>();
        for (NodoEnArmado n : nodos) {
            if (sinResponder.contains(n.codigo)) {
                pendientes.add(new Pendiente(TipoPendiente.OBJECION, Optional.of(n.id), Optional.empty(), "Responder la objeción: " + n.definicion.texto()));
            }
        }
        for (NodoEnArmado n : nodos) {
            if (rol(n) == ResultadoMapa.Rol.OCULTA) {
                pendientes.add(new Pendiente(TipoPendiente.VERIFICACION, Optional.of(n.id), Optional.empty(),
                        "Verificar la premisa oculta: " + n.definicion.texto()));
            }
        }

        List<ResultadoMapa.Nodo> nodosMapa = nodos.stream()
                .map(n -> new ResultadoMapa.Nodo(n.codigo, n.id, n.definicion.titulo(), n.definicion.texto(), rol(n), asumible(n))).toList();
        List<AfirmacionConRol> afirmaciones = nodos.stream()
                .map(n -> new AfirmacionConRol(n.id, n.definicion.texto(), TipoAfirmacion.HECHO,
                        n.raiz ? RolAfirmacion.CONCLUSION : RolAfirmacion.PREMISA, SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO))
                .toList();
        List<ArgumentoProducido> guardables = grafo.stream()
                .map(a -> new ArgumentoProducido(a, estandar, Optional.empty(), Optional.of(argdown))).toList();

        String resumen = resumen(nodos.size(), apoyos, ataques, sinResponder.size());
        ResultadoMapa valor = new ResultadoMapa(argdown, opciones.direccion(), opciones.coloresPorRol(), opciones.mostrarPesos(), estandar,
                nodosMapa, argumentosMapa, conclusiones, apoyos, ataques, sinResponder, resumen);
        return new Construido(valor, afirmaciones, pendientes, guardables);
    }

    /** Algún argumento contra tiene a este nodo como conclusión. */
    private boolean atacado(NodoEnArmado n) {
        return argumentos.stream().anyMatch(a -> a.sentido() == ResultadoMapa.Sentido.CONTRA && a.conclusion() == n);
    }

    private String motivo(ArgumentoEnArmado a, boolean aplicable, EstandarPrueba estandar) {
        List<String> sinVerificar = a.premisas().stream().filter(p -> !asumible(p)).map(p -> p.definicion.texto()).toList();
        List<String> cuestionadas = a.premisas().stream().filter(p -> asumible(p) && atacado(p)).map(p -> p.definicion.texto()).toList();
        if (aplicable) {
            if (!cuestionadas.isEmpty()) {
                return "Aplicable bajo escrutinio, aunque " + supuestos(cuestionadas) + ".";
            }
            return "Aplicable: sus premisas son supuestos sin objeción.";
        }
        if (!sinVerificar.isEmpty()) {
            String frase = "No aplicable todavía: falta verificar " + Textos.enumerarCitas(sinVerificar) + ".";
            return cuestionadas.isEmpty() || estandar == EstandarPrueba.ESCRUTINIO ? frase : frase + " Además, " + supuestos(cuestionadas) + ".";
        }
        return "No aplicable: " + supuestos(cuestionadas) + ".";
    }

    private static String supuestos(List<String> cuestionadas) {
        return cuestionadas.size() == 1
                ? "el supuesto " + Textos.enumerarCitas(cuestionadas) + " tiene una objeción"
                : "los supuestos " + Textos.enumerarCitas(cuestionadas) + " tienen objeciones";
    }

    private ResultadoMapa.Aceptabilidad aceptabilidad(NodoEnArmado conclusion, EstandarPrueba estandar, List<Argumento> grafo,
                                                      Map<ArgumentoEnArmado, Argumento> delNucleo, Map<UUID, R04Aceptabilidad.EstadoPremisa> estados) {
        boolean aceptable = R04Aceptabilidad.aceptable(conclusion.id, estandar, grafo, estados, R04Aceptabilidad.Parametros.v1());
        List<ArgumentoEnArmado> proAplicables = argumentos.stream()
                .filter(a -> a.conclusion() == conclusion && a.sentido() == ResultadoMapa.Sentido.PRO)
                .filter(a -> R04Aceptabilidad.aplicable(delNucleo.get(a), estados, estandar) && !R04Aceptabilidad.esCiclico(delNucleo.get(a), grafo))
                .toList();
        String nombre = estandar.nombre();
        if (aceptable) {
            List<String> supuestos = new ArrayList<>();
            proAplicables.forEach(a -> a.premisas().stream().filter(MapaArgumental::asumible).map(p -> p.definicion.texto())
                    .filter(t -> !supuestos.contains(t)).forEach(supuestos::add));
            String frase = supuestos.isEmpty() ? "Aceptable bajo " + nombre + "."
                    : "Aceptable bajo " + nombre + ", siempre que se sostengan " + (supuestos.size() == 1 ? "el supuesto " : "los supuestos ")
                    + Textos.enumerarCitas(supuestos) + ".";
            return new ResultadoMapa.Aceptabilidad(conclusion.codigo, true, frase);
        }
        if (proAplicables.isEmpty()) {
            return new ResultadoMapa.Aceptabilidad(conclusion.codigo, false,
                    "No aceptable bajo " + nombre + " todavía: ningún argumento a favor es aplicable.");
        }
        return new ResultadoMapa.Aceptabilidad(conclusion.codigo, false,
                "No aceptable bajo " + nombre + ": los argumentos a favor aplicables no alcanzan el estándar.");
    }

    /** "Mapa de 4 afirmaciones: 1 apoyo, 1 ataque y 1 objeción sin responder." */
    static String resumen(int afirmaciones, int apoyos, int ataques, int sinResponder) {
        return "Mapa de " + Textos.contar(afirmaciones, "afirmación", "afirmaciones") + ": "
                + (apoyos == 0 ? "ningún apoyo" : Textos.contar(apoyos, "apoyo", "apoyos")) + ", "
                + (ataques == 0 ? "ningún ataque" : Textos.contar(ataques, "ataque", "ataques")) + " y "
                + (sinResponder == 0 ? "ninguna objeción sin responder" : Textos.contar(sinResponder, "objeción", "objeciones") + " sin responder") + ".";
    }
}
