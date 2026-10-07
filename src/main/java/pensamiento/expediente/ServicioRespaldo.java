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
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.Expediente;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.PendienteGuardado;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.nucleo.puertos.RegistroAuditoria;
import pensamiento.nucleo.puertos.RegistroIdentificadores;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioConfiguracion;
import pensamiento.nucleo.puertos.RepositorioEjecucion;
import pensamiento.nucleo.puertos.RepositorioExpediente;

/**
 * Exportar e importar los datos de una persona (RF-12). Importar es idempotente por identificador: lo que ya es
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
    private final RepositorioConfiguracion configuraciones;
    private final RegistroIdentificadores identificadores;
    private final RegistroAuditoria auditoria;
    private final Reloj reloj;

    public ServicioRespaldo(RepositorioExpediente expedientes, RepositorioEjecucion ejecuciones, RepositorioConfiguracion configuraciones,
                            RegistroIdentificadores identificadores, RegistroAuditoria auditoria, Reloj reloj) {
        this.expedientes = expedientes;
        this.ejecuciones = ejecuciones;
        this.configuraciones = configuraciones;
        this.identificadores = identificadores;
        this.auditoria = auditoria;
        this.reloj = reloj;
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
                            a.rol().name().toLowerCase(), a.sentido().name().toLowerCase(), a.origen().name().toLowerCase()))
                    .toList();
            List<PaqueteDatos.PendienteDatos> suyos = pendientes.stream().filter(p -> p.ejecucionId().equals(e.id()))
                    .map(p -> new PaqueteDatos.PendienteDatos(p.pendiente().tipo().name().toLowerCase(), p.pendiente().objetoId().orElse(null),
                            p.pendiente().vence().orElse(null), p.pendiente().descripcion()))
                    .toList();
            ejs.add(new PaqueteDatos.EjecucionDatos(e.id(), e.tecnica().valor(), e.versionEsquema(), e.expedienteId().orElse(null),
                    nodo(e.config()), nodo(e.datos()), nodo(e.resultado()), e.resumen(), e.claveIdempotencia(), e.creadaEn(), afirmaciones, suyos));
        }
        auditoria.registrar(new RegistroAuditoria.Evento(Optional.of(usuarioId), institucionId, RegistroAuditoria.Accion.EXPORTAR,
                "datos-de-una-persona", Optional.of(usuarioId), reloj.ahora()));
        return new PaqueteDatos(PaqueteDatos.FORMATO, PaqueteDatos.VERSION, reloj.ahora(), persona, configs, exps, ejs);
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

    public Importacion importar(UUID usuarioId, UUID institucionId, PaqueteDatos paquete) {
        if (paquete == null || !PaqueteDatos.FORMATO.equals(paquete.formato()) || paquete.version() != PaqueteDatos.VERSION) {
            throw new ArchivoInvalido("El archivo no es un respaldo de datos de esta aplicación (versión " + PaqueteDatos.VERSION + ").");
        }
        Set<UUID> ids = new LinkedHashSet<>();
        paquete.expedientes().forEach(x -> ids.add(exigir(x.id())));
        for (PaqueteDatos.EjecucionDatos e : paquete.ejecuciones()) {
            ids.add(exigir(e.id()));
            listaSegura(e.afirmaciones()).forEach(a -> ids.add(exigir(a.id())));
        }
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
                    json(d.config()), json(d.datos()), json(d.resultado()), d.resumen() == null ? "" : d.resumen(), Optional.empty(),
                    d.claveIdempotencia() == null || d.claveIdempotencia().isBlank() ? "importada-" + d.id() : d.claveIdempotencia(),
                    d.creadaEn() == null ? reloj.ahora() : d.creadaEn());
            List<AfirmacionConRol> afirmaciones = listaSegura(d.afirmaciones()).stream().map(a -> new AfirmacionConRol(a.id(), a.texto(),
                    TipoAfirmacion.valueOf(a.tipo().toUpperCase()), RolAfirmacion.valueOf(a.rol().toUpperCase()),
                    SentidoAfirmacion.valueOf(a.sentido().toUpperCase()), OrigenAfirmacion.valueOf(a.origen().toUpperCase()))).toList();
            List<Pendiente> pendientes = listaSegura(d.pendientes()).stream().map(p -> new Pendiente(TipoPendiente.valueOf(p.tipo().toUpperCase()),
                    Optional.ofNullable(p.objetoId()), Optional.ofNullable(p.vence()), p.descripcion() == null ? "" : p.descripcion())).toList();
            ejecuciones.guardar(e, afirmaciones, pendientes);
            ejNuevas++;
        }
        for (PaqueteDatos.Configuracion c : paquete.configuraciones()) {
            configuraciones.guardar(usuarioId, institucionId, IdTecnica.de(c.tecnica()), c.versionEsquema(), json(c.valores()));
        }
        auditoria.registrar(new RegistroAuditoria.Evento(Optional.of(usuarioId), institucionId, RegistroAuditoria.Accion.IMPORTAR,
                "datos-de-una-persona", Optional.of(usuarioId), reloj.ahora()));
        return new Importacion(expNuevos, expActualizados, ejNuevas, ejYaEstaban, paquete.configuraciones().size());
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
