package pensamiento.web.expedientes;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import pensamiento.catalogo.MapeadorJson;
import pensamiento.expediente.ServicioExpedientes;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.Expediente;
import pensamiento.nucleo.Familia;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.PendienteGuardado;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.Tecnica;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioTecnica;
import pensamiento.web.formulario.LenguajeCampos;
import pensamiento.web.patrones.Modo;
import pensamiento.web.patrones.Renderizadores;
import pensamiento.web.tecnicas.MotorTecnicas;

/**
 * El expediente de muestra "La segunda sucursal de la panadería" (P02 y P22, RF-08). Decisión: no se siembra en
 * tablas de usuario. Es contenido del catálogo: se arma en memoria con los ejemplos de T28 de la panadería y se
 * pinta en modo lectura con el mismo fragmento del Expediente. Así no hay que escribir filas bajo RLS en nombre de
 * nadie, ni copiarlas al crear cada cuenta, ni borrarlas después; toda persona ve la misma muestra.
 */
@Component
public class MuestraExpediente {

    public static final String NOMBRE = "La segunda sucursal de la panadería";
    /** Los ejemplos de T28 que cuentan la historia de la panadería, en el orden de la línea de tiempo (el más reciente primero). */
    static final List<String> EJEMPLOS = List.of("El pan quemado", "Las ventas de los sábados");
    private static final UUID ID = UUID.nameUUIDFromBytes("expediente-de-muestra".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final IdTecnica T28 = IdTecnica.de("T28");

    private final RepositorioTecnica tecnicas;
    private final MotorTecnicas motor;
    private final Renderizadores renderizadores;
    private final Reloj reloj;

    public MuestraExpediente(RepositorioTecnica tecnicas, MotorTecnicas motor, Renderizadores renderizadores, Reloj reloj) {
        this.tecnicas = tecnicas;
        this.motor = motor;
        this.renderizadores = renderizadores;
        this.reloj = reloj;
    }

    public VistaExpediente vista() {
        Tecnica t28 = tecnicas.porId(T28).orElseThrow();
        Instant base = Instant.parse("2026-10-05T14:00:00Z");
        List<Ejecucion> linea = new ArrayList<>();
        List<PendienteGuardado> pendientes = new ArrayList<>();
        Map<UUID, Object> valores = new java.util.HashMap<>();
        List<Ejemplo> ejemplos = tecnicas.ejemplos(T28);
        for (int i = 0; i < EJEMPLOS.size(); i++) {
            String titulo = EJEMPLOS.get(i);
            Ejemplo ejemplo = ejemplos.stream().filter(e -> e.titulo().equals(titulo)).findFirst().orElseThrow();
            UUID idEjecucion = UUID.nameUUIDFromBytes(("muestra/" + titulo).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Contexto ctx = motor.contexto(ID, ID);
            MotorTecnicas.Evaluacion ev = motor.evaluar(t28, LenguajeCampos.mapa(ejemplo.config()), LenguajeCampos.mapa(ejemplo.datos()), ctx);
            Resultado<?> r = ev.resultado().orElseThrow();
            Instant cuando = base.minusSeconds(86_400L * i);
            linea.add(new Ejecucion(idEjecucion, ID, ID, T28, r.versionEsquema(), Optional.of(ID), ev.config(), ev.entrada(),
                    MapeadorJson.escribir(r.valor()), r.resumen(), Optional.empty(), "muestra-" + i, cuando));
            valores.put(idEjecucion, r.valor());
            r.pendientes().forEach(p -> pendientes.add(new PendienteGuardado(UUID.nameUUIDFromBytes((titulo + p.descripcion()).getBytes(
                    java.nio.charset.StandardCharsets.UTF_8)), idEjecucion, p, false)));
        }
        Expediente expediente = new Expediente(ID, ID, ID, NOMBRE, Optional.empty(), Expediente.Estado.ABIERTO, base.minusSeconds(86_400L * 3));
        List<Tecnica> todas = tecnicas.todas();
        List<ServicioExpedientes.PorFamilia> porFamilia = new ArrayList<>();
        for (Familia f : tecnicas.familias()) {
            List<Ejecucion> suyas = f.codigo().equals(t28.familia()) ? linea : List.of();
            Optional<Tecnica> sugerida = suyas.isEmpty()
                    ? todas.stream().filter(t -> t.familia().equals(f.codigo())).findFirst() : Optional.empty();
            porFamilia.add(new ServicioExpedientes.PorFamilia(f, suyas, suyas.isEmpty() ? 0 : pendientes.size(), sugerida));
        }
        ServicioExpedientes.Vista vista = new ServicioExpedientes.Vista(expediente, linea, porFamilia, pendientes, 1);
        Map<IdTecnica, Tecnica> porId = todas.stream().collect(Collectors.toMap(Tecnica::id, Function.identity()));
        ZoneId zona = reloj.zona();
        return VistaExpediente.de(vista, porId, true,
                e -> renderizadores.render(t28, Optional.empty(), "muestra-" + linea.indexOf(e), valores.get(e.id()), Modo.LECTURA), zona);
    }
}
