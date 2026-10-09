package pensamiento.expediente;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;

import pensamiento.catalogo.MapeadorJson;
import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Argumento;
import pensamiento.nucleo.ArgumentoProducido;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.EstandarPrueba;
import pensamiento.nucleo.Expediente;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.PendienteGuardado;
import pensamiento.nucleo.Prediccion;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.nucleo.puertos.RegistroAuditoria;
import pensamiento.nucleo.puertos.RegistroIdentificadores;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioArgumentos;
import pensamiento.nucleo.puertos.RepositorioConfiguracion;
import pensamiento.nucleo.puertos.RepositorioEjecucion;
import pensamiento.nucleo.puertos.RepositorioExpediente;
import pensamiento.nucleo.puertos.RepositorioPredicciones;

/**
 * Exportar e importar los datos de una persona (RF-12), con los argumentos de cada ejecución desde el hito 2, sus
 * predicciones desde el hito 4 y la biblioteca, las evidencias y las fichas de verificación desde el hito 6
 * (RespaldoDeLaBiblioteca). Importar es idempotente por identificador: lo que ya es
 * suyo se actualiza (nombre del expediente, asociación de la ejecución) y lo nuevo se agrega; nada se borra. Si
 * algún identificador ya es de otra persona, se rechaza el archivo completo. Ambas acciones quedan en auditoría.
 * La transacción la abre quien llama: si algo falla, no queda nada a medias.
 */
@Service
public class ServicioRespaldo {

    /** Tope de ejecuciones exportadas de una vez; muy por encima del uso de una persona. */
    static final int TOPE_EJECUCIONES = 100_000;

    public record Importacion(int expedientesNuevos, int expedientesActualizados, int ejecucionesNuevas, int ejecucionesYaEstaban,
                              int configuraciones) {
    }

    /** El archivo no se puede leer o no es un respaldo de esta aplicación. */
    public static class ArchivoInvalido extends IllegalArgumentException {
        public ArchivoInvalido(String mensaje) {
            super(mensaje);
        }
    }

    /** El archivo trae identificadores que ya son de otra persona. */
    public static class IdentificadorAjeno extends IllegalArgumentException {
        public IdentificadorAjeno(int cuantos) {
            super("El archivo trae " + cuantos + (cuantos == 1 ? " identificador que ya es" : " identificadores que ya son")
                    + " de otra persona. No se importó nada.");
        }
    }

    private final RepositorioExpediente expedientes;
    private final RepositorioEjecucion ejecuciones;
    private final RepositorioArgumentos argumentos;
    private final RepositorioConfiguracion configuraciones;
    private final RegistroIdentificadores identificadores;
    private final RegistroAuditoria auditoria;
    private final Reloj reloj;
    private final RepositorioPredicciones predicciones;
    private final pensamiento.nucleo.puertos.RepositorioCambiosOpinion cambios;
    private final pensamiento.nucleo.puertos.RepositorioSesiones sesiones;
    private final RespaldoDeLaBiblioteca biblioteca;

    public ServicioRespaldo(RepositorioExpediente expedientes, RepositorioEjecucion ejecuciones, RepositorioArgumentos argumentos,
                            RepositorioConfiguracion configuraciones, RegistroIdentificadores identificadores, RegistroAuditoria auditoria,
                            Reloj reloj, RepositorioPredicciones predicciones, pensamiento.nucleo.puertos.RepositorioCambiosOpinion cambios,
                            pensamiento.nucleo.puertos.RepositorioSesiones sesiones, RespaldoDeLaBiblioteca biblioteca) {
        this.biblioteca = biblioteca;
        this.cambios = cambios;
        this.sesiones = sesiones;
        this.expedientes = expedientes;
        this.ejecuciones = ejecuciones;
        this.argumentos = argumentos;
        this.configuraciones = configuraciones;
        this.identificadores = identificadores;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.predicciones = predicciones;
    }

    public PaqueteDatos exportar(UUID usuarioId, UUID institucionId, String persona) {
        List<PaqueteDatos.Configuracion> configs = new ArrayList<>();
        for (Map.Entry<IdTecnica, RepositorioConfiguracion.Guardada> c : configuraciones.todas(usuarioId).entrySet()) {
            configs.add(new PaqueteDatos.Configuracion(c.getKey().valor(), c.getValue().versionEsquema(), nodo(c.getValue().valores())));
        }
        List<PaqueteDatos.ExpedienteDatos> exps = expedientes.deUsuario(usuarioId).stream()
                .map(e -> new PaqueteDatos.ExpedienteDatos(e.id(), e.nombre(), e.estado().name().toLowerCase(), e.creadoEn()))
                .toList();
        List<PendienteGuardado> pendientes = ejecuciones.pendientes(usuarioId);
        List<PaqueteDatos.EjecucionDatos> ejs = new ArrayList<>();
        for (Ejecucion e : ejecuciones.recientes(usuarioId, TOPE_EJECUCIONES).reversed()) {
            List<PaqueteDatos.AfirmacionDatos> afirmaciones = ejecuciones.afirmacionesDe(usuarioId, e.id()).stream()
                    .map(a -> new PaqueteDatos.AfirmacionDatos(a.afirmacionId(), a.texto(), a.tipo().enBaseDeDatos(),
                            a.rol().name().toLowerCase(), a.sentido().name().toLowerCase(), a.origen().name().toLowerCase(), a.adoptada()))
                    .toList();
            List<PaqueteDatos.PendienteDatos> suyos = pendientes.stream().filter(p -> p.ejecucionId().equals(e.id()))
                    .map(p -> new PaqueteDatos.PendienteDatos(p.pendiente().tipo().name().toLowerCase(), p.pendiente().objetoId().orElse(null),
                            p.pendiente().vence().orElse(null), p.pendiente().descripcion()))
                    .toList();
            List<PaqueteDatos.ArgumentoDatos> args = argumentos.deEjecucion(usuarioId, e.id()).stream().map(g -> datos(g.argumento())).toList();
            List<PaqueteDatos.PrediccionDatos> preds = predicciones.deEjecucion(usuarioId, e.id()).stream()
                    .map(p -> new PaqueteDatos.PrediccionDatos(p.id(), p.afirmacionId(), p.confianza(), p.fechaRevision(), p.estado().toString(),
                            p.resueltaEn().orElse(null))).toList();
            List<PaqueteDatos.CambioDatos> cams = cambios.deEjecucion(usuarioId, e.id()).stream()
                    .map(c -> new PaqueteDatos.CambioDatos(c.id(), c.afirmacionId(), c.confianzaAntes(), c.confianzaDespues(), c.causa().toString(),
                            c.creadoEn())).toList();
            ejs.add(new PaqueteDatos.EjecucionDatos(e.id(), e.tecnica().valor(), e.versionEsquema(), e.expedienteId().orElse(null),
                    nodo(e.config()), nodo(e.datos()), nodo(e.resultado()), e.resumen(), e.claveIdempotencia(), e.creadaEn(),
                    e.modelo().map(m -> new PaqueteDatos.RegistroModeloDatos(m.modelo(), m.digest(), m.promptVersion(), m.temperatura(), m.semilla())).orElse(null),
                    afirmaciones, suyos, args, preds, cams));
        }
        List<PaqueteDatos.SesionDatos> ses = sesiones.deUsuario(usuarioId).reversed().stream().map(s -> sesionDatos(s, sesiones.turnos(usuarioId, s.id())))
                .toList();
        RespaldoDeLaBiblioteca.Exportado deLaBiblioteca = biblioteca.exportar(usuarioId,
                ejs.stream().flatMap(e -> e.afirmaciones().stream()).map(PaqueteDatos.AfirmacionDatos::id).toList());
        auditoria.registrar(new RegistroAuditoria.Evento(Optional.of(usuarioId), institucionId, RegistroAuditoria.Accion.EXPORTAR,
                "datos-de-una-persona", Optional.of(usuarioId), reloj.ahora()));
        return new PaqueteDatos(PaqueteDatos.FORMATO, PaqueteDatos.VERSION, reloj.ahora(), persona, configs, exps, ejs, ses, deLaBiblioteca.documentos(),
                deLaBiblioteca.evidencias(), deLaBiblioteca.verificaciones(), deLaBiblioteca.veredictos());
    }

    public String exportarComoTexto(UUID usuarioId, UUID institucionId, String persona) {
        return MapeadorJson.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(exportar(usuarioId, institucionId, persona));
    }

    public Importacion importarTexto(UUID usuarioId, UUID institucionId, String texto) {
        PaqueteDatos paquete;
        try {
            paquete = MapeadorJson.mapper().readValue(texto, PaqueteDatos.class);
        } catch (JacksonException e) {
            throw new ArchivoInvalido("El archivo no es un JSON válido.");
        }
        return importar(usuarioId, institucionId, paquete);
    }

    public Importacion importar(UUID usuarioId, UUID institucionId, PaqueteDatos recibido) {
        if (recibido == null || !PaqueteDatos.FORMATO.equals(recibido.formato())
                || !PaqueteDatos.VERSIONES_LEIBLES.contains(recibido.version())) {
            throw new ArchivoInvalido("El archivo no es un respaldo de datos de esta aplicación (versión 1 a " + PaqueteDatos.VERSION + ").");
        }
        PaqueteDatos paquete = recibido.migrado();
        Set<UUID> ids = new LinkedHashSet<>();
        paquete.expedientes().forEach(x -> ids.add(exigir(x.id())));
        for (PaqueteDatos.EjecucionDatos e : paquete.ejecuciones()) {
            ids.add(exigir(e.id()));
            listaSegura(e.afirmaciones()).forEach(a -> ids.add(exigir(a.id())));
            listaSegura(e.argumentos()).forEach(a -> ids.add(exigir(a.id())));
            listaSegura(e.predicciones()).forEach(p -> ids.add(exigir(p == null ? null : p.id())));
            listaSegura(e.cambios()).forEach(c -> ids.add(exigir(c == null ? null : c.id())));
        }
        for (PaqueteDatos.SesionDatos s : paquete.sesiones()) {
            if (s == null) {
                throw new ArchivoInvalido("El archivo trae un elemento sin identificador.");
            }
            ids.add(exigir(s.id()));
            listaSegura(s.turnos()).forEach(t -> ids.add(exigir(t == null ? null : t.id())));
        }
        ids.addAll(biblioteca.identificadores(paquete));
        long ajenos = ids.stream().filter(id -> identificadores.deOtroUsuario(usuarioId, id)).count();
        if (ajenos > 0) {
            throw new IdentificadorAjeno((int) ajenos);
        }
        int expNuevos = 0;
        int expActualizados = 0;
        for (PaqueteDatos.ExpedienteDatos x : paquete.expedientes()) {
            boolean existia = expedientes.porId(usuarioId, x.id()).isPresent();
            expedientes.guardar(new Expediente(x.id(), usuarioId, institucionId, nombre(x.nombre()), Optional.empty(),
                    "cerrado".equals(x.estado()) ? Expediente.Estado.CERRADO : Expediente.Estado.ABIERTO,
                    x.creadoEn() == null ? reloj.ahora() : x.creadoEn()));
            if (existia) {
                expActualizados++;
            } else {
                expNuevos++;
            }
        }
        int ejNuevas = 0;
        int ejYaEstaban = 0;
        for (PaqueteDatos.EjecucionDatos d : paquete.ejecuciones()) {
            Optional<UUID> expediente = Optional.ofNullable(d.expedienteId()).filter(x -> expedientes.porId(usuarioId, x).isPresent());
            if (ejecuciones.porId(usuarioId, d.id()).isPresent()) {
                ejecuciones.asociar(usuarioId, d.id(), expediente);
                ejYaEstaban++;
                continue;
            }
            Ejecucion e = new Ejecucion(d.id(), usuarioId, institucionId, IdTecnica.de(d.tecnica()), d.versionEsquema(), expediente,
                    json(d.config()), json(d.datos()), json(d.resultado()), d.resumen() == null ? "" : d.resumen(),
                    Optional.ofNullable(d.modelo()).map(m -> new Ejecucion.RegistroModelo(m.modelo(), m.digest(), m.promptVersion(), m.temperatura(), m.semilla())),
                    d.claveIdempotencia() == null || d.claveIdempotencia().isBlank() ? "importada-" + d.id() : d.claveIdempotencia(),
                    d.creadaEn() == null ? reloj.ahora() : d.creadaEn());
            List<AfirmacionConRol> afirmaciones = listaSegura(d.afirmaciones()).stream().map(a -> new AfirmacionConRol(a.id(), a.texto(),
                    TipoAfirmacion.valueOf(a.tipo().toUpperCase()), RolAfirmacion.valueOf(a.rol().toUpperCase()),
                    SentidoAfirmacion.valueOf(a.sentido().toUpperCase()), OrigenAfirmacion.valueOf(a.origen().toUpperCase()),
                    a.adoptada() == null ? !"modelo".equalsIgnoreCase(a.origen()) : a.adoptada())).toList();
            List<Pendiente> pendientes = listaSegura(d.pendientes()).stream().map(p -> new Pendiente(TipoPendiente.valueOf(p.tipo().toUpperCase()),
                    Optional.ofNullable(p.objetoId()), Optional.ofNullable(p.vence()), p.descripcion() == null ? "" : p.descripcion())).toList();
            ejecuciones.guardar(e, afirmaciones, pendientes);
            List<ArgumentoProducido> suyos = listaSegura(d.argumentos()).stream().map(ServicioRespaldo::producido).toList();
            Set<UUID> deLaEjecucion = new java.util.HashSet<>(afirmaciones.stream().map(AfirmacionConRol::afirmacionId).toList());
            for (ArgumentoProducido p : suyos) {
                if (!deLaEjecucion.contains(p.argumento().conclusionId())
                        || p.argumento().premisas().stream().anyMatch(x -> !deLaEjecucion.contains(x.afirmacionId()))) {
                    throw new ArchivoInvalido("Un argumento del archivo apunta a una afirmación que no es de su ejecución.");
                }
            }
            argumentos.guardar(usuarioId, institucionId, e.id(), suyos);
            for (PaqueteDatos.PrediccionDatos p : listaSegura(d.predicciones())) {
                predicciones.restaurar(usuarioId, institucionId, prediccion(p, e.id(), deLaEjecucion));
            }
            for (PaqueteDatos.CambioDatos c : listaSegura(d.cambios())) {
                cambios.restaurar(usuarioId, institucionId, cambio(c, e.id(), deLaEjecucion));
            }
            ejNuevas++;
        }
        for (PaqueteDatos.SesionDatos s : paquete.sesiones()) {
            restaurarSesion(usuarioId, institucionId, s);
        }
        Set<UUID> afirmacionesDelArchivo = new java.util.HashSet<>();
        paquete.ejecuciones().forEach(e -> listaSegura(e.afirmaciones()).forEach(a -> afirmacionesDelArchivo.add(a.id())));
        biblioteca.importar(usuarioId, institucionId, paquete, afirmacionesDelArchivo);
        for (PaqueteDatos.Configuracion c : paquete.configuraciones()) {
            configuraciones.guardar(usuarioId, institucionId, IdTecnica.de(c.tecnica()), c.versionEsquema(), json(c.valores()));
        }
        auditoria.registrar(new RegistroAuditoria.Evento(Optional.of(usuarioId), institucionId, RegistroAuditoria.Accion.IMPORTAR,
                "datos-de-una-persona", Optional.of(usuarioId), reloj.ahora()));
        return new Importacion(expNuevos, expActualizados, ejNuevas, ejYaEstaban, paquete.configuraciones().size());
    }

    /** Un cambio de opinión del archivo, validado: su afirmación es de la ejecución y la causa es una de las de la tabla. */
    private static pensamiento.nucleo.CambioOpinion cambio(PaqueteDatos.CambioDatos d, UUID ejecucionId, Set<UUID> deLaEjecucion) {
        if (d.afirmacionId() == null || !deLaEjecucion.contains(d.afirmacionId()) || d.causa() == null || d.creadoEn() == null) {
            throw new ArchivoInvalido("Un cambio de opinión del archivo apunta a una afirmación que no es de su ejecución.");
        }
        try {
            return new pensamiento.nucleo.CambioOpinion(d.id(), d.afirmacionId(), "", d.confianzaAntes(), d.confianzaDespues(),
                    pensamiento.nucleo.CambioOpinion.Causa.de(d.causa()), Optional.of(ejecucionId), d.creadoEn());
        } catch (IllegalArgumentException e) {
            throw new ArchivoInvalido("Un cambio de opinión del archivo no es válido.");
        }
    }

    private static PaqueteDatos.SesionDatos sesionDatos(pensamiento.nucleo.SesionConsejero s, List<pensamiento.nucleo.TurnoConsejero> turnos) {
        return new PaqueteDatos.SesionDatos(s.id(), s.expedienteId().orElse(null), s.modo().toString(), s.postura(),
                s.razones().stream().map(r -> new PaqueteDatos.RazonDatos(r.texto(), r.apoyo())).toList(), nodo(s.config()), s.usaModelo(),
                s.confianzaAntes().orElse(null), s.cierrePedido(), s.estado().toString(), s.reflexion().orElse(null), s.confianzaDespues().orElse(null),
                s.ejecucionId().orElse(null), s.creadaEn(), s.cerradaEn().orElse(null),
                turnos.stream().map(t -> new PaqueteDatos.TurnoDatos(t.id(), t.numero(), t.rol().toString(), t.paso(), t.texto(), t.origen().toString(),
                        t.estado().toString(), t.intentos(), t.modelo().map(m -> new PaqueteDatos.RegistroModeloDatos(m.modelo(), m.digest(), m.promptVersion(),
                        m.temperatura(), m.semilla())).orElse(null), t.elementoPropuesto().orElse(null), t.porquePropuesto().orElse(null),
                        t.propuestaAdoptada(), t.creadoEn())).toList());
    }

    /**
     * Restaura una sesión del Consejero tal como estaba. El expediente y la ejecución quedan solo si ya son de la persona; un
     * turno que estaba redactando entra como listo, con el texto que tenía.
     */
    private void restaurarSesion(UUID usuarioId, UUID institucionId, PaqueteDatos.SesionDatos d) {
        try {
            pensamiento.nucleo.SesionConsejero.Estado estado = pensamiento.nucleo.SesionConsejero.Estado.valueOf(obligatorio(d.estado()).toUpperCase());
            if (estado == pensamiento.nucleo.SesionConsejero.Estado.CERRADA && d.cerradaEn() == null || d.postura() == null || d.postura().isBlank() || d.config() == null) {
                throw new ArchivoInvalido("Una sesión del Consejero del archivo no es válida.");
            }
            pensamiento.nucleo.SesionConsejero s = new pensamiento.nucleo.SesionConsejero(d.id(), usuarioId, institucionId,
                    Optional.ofNullable(d.expedienteId()).filter(x -> expedientes.porId(usuarioId, x).isPresent()),
                    pensamiento.nucleo.SesionConsejero.Modo.de(obligatorio(d.modo())), d.postura(),
                    listaSegura(d.razones()).stream().map(r -> new pensamiento.nucleo.SesionConsejero.Razon(r.texto(), r.apoyo())).toList(), json(d.config()),
                    d.usaModelo(), Optional.ofNullable(d.confianzaAntes()), d.cierrePedido(), estado, Optional.ofNullable(d.reflexion()),
                    Optional.ofNullable(d.confianzaDespues()), Optional.ofNullable(d.ejecucionId()).filter(x -> ejecuciones.porId(usuarioId, x).isPresent()),
                    d.creadaEn() == null ? reloj.ahora() : d.creadaEn(), Optional.ofNullable(d.cerradaEn()));
            List<pensamiento.nucleo.TurnoConsejero> turnos = listaSegura(d.turnos()).stream().map(ServicioRespaldo::obligatorio).map(t -> new pensamiento.nucleo.TurnoConsejero(t.id(), d.id(),
                    t.numero(), pensamiento.nucleo.TurnoConsejero.Rol.valueOf(obligatorio(t.rol()).toUpperCase()), t.paso(), t.texto(),
                    pensamiento.nucleo.TurnoConsejero.Origen.valueOf(obligatorio(t.origen()).toUpperCase()), pensamiento.nucleo.TurnoConsejero.Estado.LISTO, t.intentos(),
                    Optional.ofNullable(t.modelo()).map(m -> new Ejecucion.RegistroModelo(m.modelo(), m.digest(), m.promptVersion(), m.temperatura(), m.semilla())),
                    Optional.ofNullable(t.elementoPropuesto()), Optional.ofNullable(t.porquePropuesto()), t.propuestaAdoptada(),
                    t.creadoEn() == null ? reloj.ahora() : t.creadoEn())).toList();
            sesiones.restaurar(usuarioId, institucionId, s, turnos);
        } catch (ArchivoInvalido e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new ArchivoInvalido("Una sesión del Consejero del archivo no es válida.");
        }
    }

    /** Una predicción del archivo, validada: su afirmación es de la ejecución y una resuelta trae su fecha de resolución. */
    private static Prediccion prediccion(PaqueteDatos.PrediccionDatos d, UUID ejecucionId, Set<UUID> deLaEjecucion) {
        if (d.afirmacionId() == null || !deLaEjecucion.contains(d.afirmacionId()) || d.fechaRevision() == null || d.resultado() == null) {
            throw new ArchivoInvalido("Una predicción del archivo apunta a una afirmación que no es de su ejecución.");
        }
        try {
            Prediccion.Estado estado = Prediccion.Estado.valueOf(d.resultado().toUpperCase());
            return new Prediccion(d.id(), ejecucionId, d.afirmacionId(), "", d.confianza(), d.fechaRevision(), estado,
                    Optional.ofNullable(d.resueltaEn()));
        } catch (IllegalArgumentException e) {
            throw new ArchivoInvalido("Una predicción del archivo no es válida.");
        }
    }

    private static PaqueteDatos.ArgumentoDatos datos(ArgumentoProducido p) {
        Argumento a = p.argumento();
        return new PaqueteDatos.ArgumentoDatos(a.id(), a.conclusionId(), p.esquemaId().orElse(null), a.peso(), a.sentido().name().toLowerCase(),
                p.estandar().enBaseDeDatos(), p.textoArgdown().orElse(null),
                a.premisas().stream().map(x -> new PaqueteDatos.PremisaDatos(x.afirmacionId(), x.orden(), x.asumible())).toList());
    }

    private static ArgumentoProducido producido(PaqueteDatos.ArgumentoDatos d) {
        if (d == null || d.sentido() == null || d.estandar() == null || d.premisas().stream().anyMatch(java.util.Objects::isNull)) {
            throw new ArchivoInvalido("Un argumento del archivo no es válido.");
        }
        try {
            return new ArgumentoProducido(new Argumento(exigir(d.id()), exigir(d.conclusionId()),
                    d.premisas().stream().map(x -> new Argumento.Premisa(exigir(x.afirmacionId()), x.orden(), x.asumible())).toList(), d.peso(),
                    Argumento.Sentido.valueOf(d.sentido().toUpperCase())), EstandarPrueba.valueOf(d.estandar().toUpperCase()),
                    Optional.ofNullable(d.esquemaId()), Optional.ofNullable(d.textoArgdown()));
        } catch (IllegalArgumentException e) {
            throw new ArchivoInvalido("Un argumento del archivo no es válido.");
        }
    }

    /** Un valor que el archivo tiene que traer en una sesión del Consejero. */
    private static <T> T obligatorio(T valor) {
        if (valor == null) {
            throw new ArchivoInvalido("Una sesión del Consejero del archivo no es válida.");
        }
        return valor;
    }

    private static UUID exigir(UUID id) {
        if (id == null) {
            throw new ArchivoInvalido("El archivo trae un elemento sin identificador.");
        }
        return id;
    }

    private static String nombre(String nombre) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty() || limpio.length() > ServicioExpedientes.LARGO_NOMBRE) {
            throw new ArchivoInvalido("Un expediente del archivo no tiene un nombre válido.");
        }
        return limpio;
    }

    private static <T> List<T> listaSegura(List<T> lista) {
        return lista == null ? List.of() : lista;
    }

    private static tools.jackson.databind.JsonNode nodo(Json json) {
        return MapeadorJson.mapper().readTree(json.texto());
    }

    private static Json json(tools.jackson.databind.JsonNode nodo) {
        return nodo == null || nodo.isNull() ? Json.VACIO : new Json(nodo.toString());
    }
}
