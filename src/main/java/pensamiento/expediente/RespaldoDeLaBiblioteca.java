package pensamiento.expediente;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import pensamiento.nucleo.Afirmacion;
import pensamiento.nucleo.Documento;
import pensamiento.nucleo.EstadoAfirmacion;
import pensamiento.nucleo.Evidencia;
import pensamiento.nucleo.EvidenciaGuardada;
import pensamiento.nucleo.FichaFuente;
import pensamiento.nucleo.Fragmento;
import pensamiento.nucleo.Fuente;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.Verificacion;
import pensamiento.nucleo.puertos.Biblioteca;
import pensamiento.nucleo.puertos.ColaTrabajos;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioEvidencias;
import pensamiento.nucleo.puertos.RepositorioVerificaciones;

/**
 * La parte del respaldo de una persona que agrega el hito 6 (paquete de datos versión 5): sus documentos indexados con el
 * texto de cada fragmento (sin el archivo original ni los vectores), sus fuentes con sus evidencias, las preguntas marcadas en
 * cada ficha y el veredicto de cada afirmación. Importar es idempotente por identificador y se apoya en lo que ya importó
 * ServicioRespaldo: las evidencias solo pueden ser sobre afirmaciones de ejecuciones del archivo. Un documento restaurado
 * queda privado y se vuelve a vectorizar.
 */
@Service
public class RespaldoDeLaBiblioteca {

    /** El tipo de trabajo que vectoriza un documento (el mismo que encola el indexador). */
    static final String VECTORIZAR = "vectorizar";

    /** Lo que esta parte agrega al paquete. */
    public record Exportado(List<PaqueteDatos.DocumentoDatos> documentos, List<PaqueteDatos.EvidenciaDatos> evidencias,
                            List<PaqueteDatos.VerificacionDatos> verificaciones, List<PaqueteDatos.VeredictoDatos> veredictos) {
    }

    private final RepositorioEvidencias evidencias;
    private final RepositorioVerificaciones verificaciones;
    private final Biblioteca biblioteca;
    private final ColaTrabajos cola;
    private final Reloj reloj;

    public RespaldoDeLaBiblioteca(RepositorioEvidencias evidencias, RepositorioVerificaciones verificaciones, Biblioteca biblioteca, ColaTrabajos cola,
                                  Reloj reloj) {
        this.evidencias = evidencias;
        this.verificaciones = verificaciones;
        this.biblioteca = biblioteca;
        this.cola = cola;
        this.reloj = reloj;
    }

    /** @param afirmaciones las de las ejecuciones que se exportan: de ellas sale el veredicto que se guarda */
    public Exportado exportar(UUID usuarioId, Collection<UUID> afirmaciones) {
        List<PaqueteDatos.DocumentoDatos> docs = new ArrayList<>();
        for (Documento d : biblioteca.visibles(usuarioId).reversed()) {
            if (!d.esDe(usuarioId) || d.estado() != Documento.Estado.INDEXADO) {
                continue;
            }
            docs.add(new PaqueteDatos.DocumentoDatos(d.id(), d.nombre(), d.tipo().enBaseDeDatos(), d.hash(), d.tamano(), d.paginas().orElse(null),
                    d.creadoEn(), biblioteca.fragmentos(usuarioId, d.id()).stream()
                    .map(f -> new PaqueteDatos.FragmentoDatos(f.id(), f.orden(), f.texto(), f.pagina().orElse(null))).toList()));
        }
        List<PaqueteDatos.EvidenciaDatos> evs = evidencias.deUsuario(usuarioId).stream().map(RespaldoDeLaBiblioteca::datos).toList();
        List<PaqueteDatos.VerificacionDatos> vers = verificaciones.deUsuario(usuarioId).stream()
                .map(v -> new PaqueteDatos.VerificacionDatos(v.afirmacionId(), v.preguntasRespondidas(), v.actualizadaEn().orElse(null))).toList();
        List<PaqueteDatos.VeredictoDatos> veredictos = new ArrayList<>();
        for (UUID id : new LinkedHashSet<>(afirmaciones)) {
            verificaciones.afirmacion(usuarioId, id).filter(RespaldoDeLaBiblioteca::tieneVeredicto)
                    .ifPresent(a -> veredictos.add(new PaqueteDatos.VeredictoDatos(a.id(), a.tipo().enBaseDeDatos(), a.estado().name().toLowerCase(),
                            a.fuerzaNeta(), a.confianza().orElse(null))));
        }
        return new Exportado(docs, evs, vers, veredictos);
    }

    private static boolean tieneVeredicto(Afirmacion a) {
        return a.estado() != EstadoAfirmacion.SIN_VERIFICAR || a.fuerzaNeta() != 0 || a.confianza().isPresent();
    }

    /** Los identificadores nuevos que trae esta parte del archivo, para rechazarlo si alguno ya es de otra persona. */
    public Set<UUID> identificadores(PaqueteDatos p) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (PaqueteDatos.DocumentoDatos d : p.documentos()) {
            ids.add(exigir(d == null ? null : d.id()));
            d.fragmentos().forEach(f -> ids.add(exigir(f == null ? null : f.id())));
        }
        for (PaqueteDatos.EvidenciaDatos e : p.evidencias()) {
            ids.add(exigir(e == null ? null : e.id()));
            ids.add(exigir(e.fuente() == null ? null : e.fuente().id()));
        }
        return ids;
    }

    /**
     * Importa documentos, evidencias, fichas y veredictos. Las evidencias, fichas y veredictos solo pueden ser sobre afirmaciones
     * de ejecuciones del archivo, que ServicioRespaldo ya importó.
     */
    public void importar(UUID usuarioId, UUID institucionId, PaqueteDatos p, Set<UUID> afirmacionesDelArchivo) {
        Set<UUID> fragmentosRestaurados = new HashSet<>();
        Set<UUID> documentosDisponibles = new HashSet<>();
        for (PaqueteDatos.DocumentoDatos d : p.documentos()) {
            Documento documento;
            List<Fragmento> fragmentos;
            try {
                documento = new Documento(d.id(), usuarioId, obligatorio(d.nombre()), Documento.Tipo.valueOf(obligatorio(d.tipo()).toUpperCase()),
                        Documento.Estado.INDEXADO, false, obligatorio(d.hash()), d.tamano(), Optional.ofNullable(d.paginas()), Optional.empty(), false,
                        d.fragmentos().size(), 0, d.creadoEn() == null ? reloj.ahora() : d.creadoEn());
                fragmentos = d.fragmentos().stream().map(RespaldoDeLaBiblioteca::obligatorio)
                        .map(f -> new Fragmento(obligatorio(f.id()), d.id(), f.orden(), obligatorio(f.texto()), Optional.ofNullable(f.pagina())))
                        .toList();
            } catch (IllegalArgumentException e) {
                throw new ServicioRespaldo.ArchivoInvalido("Un documento de la biblioteca del archivo no es válido.");
            }
            if (biblioteca.restaurar(usuarioId, institucionId, documento, fragmentos)) {
                fragmentosRestaurados.addAll(fragmentos.stream().map(Fragmento::id).toList());
                cola.encolar(VECTORIZAR, new pensamiento.nucleo.Json("{\"usuario\":\"" + usuarioId + "\",\"institucion\":\"" + institucionId
                        + "\",\"documento\":\"" + d.id() + "\"}"), reloj.ahora());
            }
            if (biblioteca.porId(usuarioId, d.id()).filter(x -> x.esDe(usuarioId)).isPresent()) {
                documentosDisponibles.add(d.id());
            }
        }
        for (PaqueteDatos.EvidenciaDatos e : p.evidencias()) {
            if (e.afirmacionId() == null || !afirmacionesDelArchivo.contains(e.afirmacionId())) {
                throw new ServicioRespaldo.ArchivoInvalido("Una evidencia del archivo apunta a una afirmación que no es de sus ejecuciones.");
            }
            evidencias.guardar(usuarioId, institucionId, evidencia(e, fragmentosRestaurados, documentosDisponibles, usuarioId));
        }
        for (PaqueteDatos.VerificacionDatos v : p.verificaciones()) {
            if (v.afirmacionId() == null || !afirmacionesDelArchivo.contains(v.afirmacionId())) {
                throw new ServicioRespaldo.ArchivoInvalido("Una ficha de verificación del archivo apunta a una afirmación que no es de sus ejecuciones.");
            }
            verificaciones.marcarPreguntas(usuarioId, institucionId, v.afirmacionId(), v.preguntas(),
                    v.actualizadaEn() == null ? reloj.ahora() : v.actualizadaEn());
        }
        for (PaqueteDatos.VeredictoDatos v : p.veredictos()) {
            if (v.afirmacionId() == null || !afirmacionesDelArchivo.contains(v.afirmacionId())) {
                throw new ServicioRespaldo.ArchivoInvalido("Un veredicto del archivo apunta a una afirmación que no es de sus ejecuciones.");
            }
            try {
                verificaciones.guardarVeredicto(usuarioId, v.afirmacionId(), new Verificacion.Veredicto(
                        TipoAfirmacion.valueOf(obligatorio(v.tipo()).toUpperCase()), EstadoAfirmacion.valueOf(obligatorio(v.estado()).toUpperCase()),
                        v.fuerzaNeta(), Optional.ofNullable(v.confianza())));
            } catch (IllegalArgumentException e) {
                throw new ServicioRespaldo.ArchivoInvalido("Un veredicto del archivo no es válido.");
            }
        }
    }

    private static EvidenciaGuardada evidencia(PaqueteDatos.EvidenciaDatos e, Set<UUID> fragmentos, Set<UUID> documentos, UUID usuarioId) {
        try {
            PaqueteDatos.FuenteDatos f = obligatorio(e.fuente());
            Optional<FichaFuente.Craap> craap = f.craap() == null || f.craap().size() != 5 ? Optional.empty()
                    : Optional.of(new FichaFuente.Craap(f.craap().get(0), f.craap().get(1), f.craap().get(2), f.craap().get(3), f.craap().get(4)));
            Optional<UUID> documento = Optional.ofNullable(f.documentoId()).filter(documentos::contains);
            FichaFuente ficha = new FichaFuente(f.id(), obligatorio(f.titulo()), Optional.ofNullable(f.autor()), Optional.ofNullable(f.fecha()),
                    Fuente.TipoFuente.valueOf(obligatorio(f.tipo()).toUpperCase()),
                    Optional.ofNullable(f.diseno()).map(x -> Fuente.DisenoEstudio.valueOf(x.toUpperCase())), Optional.ofNullable(f.grupo()),
                    f.independiente(), f.original(), Optional.ofNullable(f.puntajeCraap()), craap,
                    new FichaFuente.Sift(f.siftInvestigue(), f.siftCobertura(), f.siftContexto()), documento, Optional.ofNullable(f.documentoNombre()),
                    Optional.ofNullable(f.pagina()));
            return new EvidenciaGuardada(obligatorio(e.id()), e.afirmacionId(), ficha,
                    Optional.ofNullable(e.fragmentoId()).filter(fragmentos::contains), obligatorio(e.pasaje()),
                    Evidencia.Postura.valueOf(obligatorio(e.postura()).toUpperCase()), e.fuerza(),
                    Evidencia.EtiquetadaPor.valueOf(obligatorio(e.etiquetadaPor()).toUpperCase()), e.adoptada());
        } catch (IllegalArgumentException ex) {
            throw new ServicioRespaldo.ArchivoInvalido("Una evidencia del archivo no es válida.");
        }
    }

    private static PaqueteDatos.EvidenciaDatos datos(EvidenciaGuardada e) {
        FichaFuente f = e.fuente();
        PaqueteDatos.FuenteDatos fuente = new PaqueteDatos.FuenteDatos(f.id(), f.titulo(), f.autor().orElse(null), f.fecha().orElse(null),
                f.tipo().name().toLowerCase(), f.disenoEstudio().map(d -> d.name().toLowerCase()).orElse(null), f.grupoOrigen().orElse(null),
                f.independiente(), f.accesoOriginal(), f.puntajeCraap().orElse(null), f.craap().map(FichaFuente.Craap::valores).orElse(null),
                f.sift().investigue(), f.sift().cobertura(), f.sift().contexto(), f.documentoId().orElse(null), f.documentoNombre().orElse(null),
                f.pagina().orElse(null));
        return new PaqueteDatos.EvidenciaDatos(e.id(), e.afirmacionId(), e.fragmentoId().orElse(null), e.pasaje(), e.postura().name().toLowerCase(),
                e.fuerza(), e.etiquetadaPor().name().toLowerCase(), e.adoptada(), fuente);
    }

    private static UUID exigir(UUID id) {
        if (id == null) {
            throw new ServicioRespaldo.ArchivoInvalido("El archivo trae un elemento sin identificador.");
        }
        return id;
    }

    private static <T> T obligatorio(T valor) {
        if (valor == null) {
            throw new IllegalArgumentException("falta un campo obligatorio");
        }
        return valor;
    }

}
