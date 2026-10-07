package pensamiento.expediente;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.Expediente;
import pensamiento.nucleo.Familia;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.PendienteGuardado;
import pensamiento.nucleo.RelacionTecnica;
import pensamiento.nucleo.Tecnica;
import pensamiento.nucleo.Uuid7;
import pensamiento.nucleo.puertos.RegistroAuditoria;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioEjecucion;
import pensamiento.nucleo.puertos.RepositorioExpediente;
import pensamiento.nucleo.puertos.RepositorioTecnica;

/**
 * Expediente (P09, RF-07): un asunto con nombre al que se asocia cualquier ejecución. Crea, asocia, borra con
 * borrado lógico (sus ejecuciones quedan sin expediente) y arma la vista: línea de tiempo, resumen por familia
 * y "qué falta para cerrar" leído de la proyección de pendientes. Las transacciones las abre quien llama.
 */
@Service
public class ServicioExpedientes {

    public static final int LARGO_NOMBRE = 120;

    /** Lo que se ve de una familia en el resumen: cuántas ejecuciones, qué quedó pendiente y una técnica sugerida si no hay nada. */
    public record PorFamilia(Familia familia, List<Ejecucion> ejecuciones, int pendientes, Optional<Tecnica> sugerida) {
        public boolean vacia() {
            return ejecuciones.isEmpty();
        }
    }

    public record Vista(Expediente expediente, List<Ejecucion> lineaDeTiempo, List<PorFamilia> porFamilia,
                        List<PendienteGuardado> faltaParaCerrar, long familiasConEjecuciones) {
    }

    public static class NombreInvalido extends IllegalArgumentException {
        public NombreInvalido() {
            super("El nombre del expediente es obligatorio y tiene como máximo " + LARGO_NOMBRE + " caracteres.");
        }
    }

    private final RepositorioExpediente expedientes;
    private final RepositorioEjecucion ejecuciones;
    private final RepositorioTecnica tecnicas;
    private final RegistroAuditoria auditoria;
    private final Reloj reloj;

    public ServicioExpedientes(RepositorioExpediente expedientes, RepositorioEjecucion ejecuciones, RepositorioTecnica tecnicas,
                               RegistroAuditoria auditoria, Reloj reloj) {
        this.expedientes = expedientes;
        this.ejecuciones = ejecuciones;
        this.tecnicas = tecnicas;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public Expediente crear(UUID usuarioId, UUID institucionId, String nombre) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty() || limpio.length() > LARGO_NOMBRE) {
            throw new NombreInvalido();
        }
        Expediente creado = expedientes.guardar(new Expediente(Uuid7.en(reloj.ahora()), usuarioId, institucionId, limpio,
                Optional.empty(), Expediente.Estado.ABIERTO, reloj.ahora()));
        auditar(usuarioId, institucionId, RegistroAuditoria.Accion.CREAR, creado.id());
        return creado;
    }

    /**
     * Asocia una ejecución a un expediente existente, a uno nuevo con ese nombre, o la desasocia si ambos vienen
     * vacíos. Vacío si la ejecución o el expediente no existen o son de otra persona.
     */
    public Optional<Optional<Expediente>> asociar(UUID usuarioId, UUID institucionId, UUID ejecucionId, Optional<UUID> expedienteId,
                                                  Optional<String> nombreNuevo) {
        if (ejecuciones.porId(usuarioId, ejecucionId).isEmpty()) {
            return Optional.empty();
        }
        Optional<Expediente> destino;
        if (nombreNuevo.filter(n -> !n.isBlank()).isPresent()) {
            destino = Optional.of(crear(usuarioId, institucionId, nombreNuevo.get()));
        } else if (expedienteId.isPresent()) {
            destino = expedientes.porId(usuarioId, expedienteId.get());
            if (destino.isEmpty()) {
                return Optional.empty();
            }
        } else {
            destino = Optional.empty();
        }
        ejecuciones.asociar(usuarioId, ejecucionId, destino.map(Expediente::id));
        return Optional.of(destino);
    }

    /** Borrado lógico: primero desasocia sus ejecuciones, que siguen en el historial de cada técnica. */
    public boolean borrar(UUID usuarioId, UUID institucionId, UUID expedienteId) {
        if (expedientes.porId(usuarioId, expedienteId).isEmpty()) {
            return false;
        }
        for (Ejecucion e : ejecuciones.porExpediente(usuarioId, expedienteId)) {
            ejecuciones.asociar(usuarioId, e.id(), Optional.empty());
        }
        boolean borrado = expedientes.borrar(usuarioId, expedienteId, reloj.ahora());
        if (borrado) {
            auditar(usuarioId, institucionId, RegistroAuditoria.Accion.BORRAR, expedienteId);
        }
        return borrado;
    }

    public Optional<Vista> vista(UUID usuarioId, UUID expedienteId) {
        return expedientes.porId(usuarioId, expedienteId).map(expediente -> {
            List<Ejecucion> linea = ejecuciones.porExpediente(usuarioId, expedienteId);
            Set<UUID> ids = linea.stream().map(Ejecucion::id).collect(Collectors.toSet());
            List<PendienteGuardado> pendientes = ejecuciones.pendientes(usuarioId).stream().filter(p -> ids.contains(p.ejecucionId())).toList();
            List<Tecnica> todas = tecnicas.todas();
            Set<IdTecnica> usadas = linea.stream().map(Ejecucion::tecnica).collect(Collectors.toSet());
            List<PorFamilia> porFamilia = new ArrayList<>();
            for (Familia f : tecnicas.familias()) {
                Set<IdTecnica> deLaFamilia = todas.stream().filter(t -> t.familia().equals(f.codigo())).map(Tecnica::id).collect(Collectors.toSet());
                List<Ejecucion> suyas = linea.stream().filter(e -> deLaFamilia.contains(e.tecnica())).toList();
                Set<UUID> idsSuyas = suyas.stream().map(Ejecucion::id).collect(Collectors.toSet());
                int pendientesSuyos = (int) pendientes.stream().filter(p -> idsSuyas.contains(p.ejecucionId())).count();
                Optional<Tecnica> sugerida = suyas.isEmpty() ? sugerida(f, todas, usadas) : Optional.empty();
                porFamilia.add(new PorFamilia(f, suyas, pendientesSuyos, sugerida));
            }
            long conEjecuciones = porFamilia.stream().filter(pf -> !pf.vacia()).count();
            return new Vista(expediente, linea, porFamilia, pendientes, conEjecuciones);
        });
    }

    /**
     * Para una familia sin ejecuciones: primero una técnica relacionada con las ya usadas (las relaciones tipadas
     * alimentan las sugerencias), luego una activa de la familia, luego la primera de la familia.
     */
    private Optional<Tecnica> sugerida(Familia f, List<Tecnica> todas, Set<IdTecnica> usadas) {
        List<Tecnica> deLaFamilia = todas.stream().filter(t -> t.familia().equals(f.codigo()))
                .sorted(Comparator.comparing(Tecnica::id)).toList();
        for (IdTecnica usada : usadas) {
            for (RelacionTecnica r : tecnicas.relaciones(usada)) {
                IdTecnica otra = r.otra(usada);
                Optional<Tecnica> relacionada = deLaFamilia.stream().filter(t -> t.id().equals(otra)).findFirst();
                if (relacionada.isPresent()) {
                    return relacionada;
                }
            }
        }
        return deLaFamilia.stream().filter(t -> !t.estaPendiente()).findFirst().or(() -> deLaFamilia.stream().findFirst());
    }

    private void auditar(UUID usuarioId, UUID institucionId, RegistroAuditoria.Accion accion, UUID objeto) {
        auditoria.registrar(new RegistroAuditoria.Evento(Optional.of(usuarioId), institucionId, accion, "expediente", Optional.of(objeto), reloj.ahora()));
    }
}
