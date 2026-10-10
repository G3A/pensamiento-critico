package pensamiento.flujos;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import pensamiento.catalogo.MapeadorJson;
import pensamiento.expediente.GuardadoDeEjecuciones;
import pensamiento.nucleo.CambioOpinion;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.Uuid7;
import pensamiento.nucleo.Validacion;
import pensamiento.nucleo.puertos.RegistroDeRazonamiento;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.tecnicas.f8.EjecutorCambiosOpinion;
import pensamiento.tecnicas.f8.EjecutorDiarioRazonamiento;
import pensamiento.tecnicas.f8.ResultadoCambiosOpinion;
import pensamiento.tecnicas.f8.ResultadoDiarioRazonamiento;

/**
 * Módulo T · Registro de cambios de opinión (P20) y diario de razonamiento: arma con los datos guardados de la persona la
 * entrada de T46 · Registro de cambios de opinión (todos los cambios de T08, el Consejero y la ficha de verificación, y las
 * posturas) y la de T45 · Diario de razonamiento (sus ejecuciones de la ventana), y las calcula con las mismas reglas que las
 * fichas, sin tope de filas. Registrar a mano un cambio guarda una ejecución de T46 con el cambio declarado (R05). La
 * transacción la abre quien llama.
 */
@Service
public class RegistroDeCambios {

    /** Lo que muestra P20. */
    public record Registro(ResultadoCambiosOpinion cambios, List<String> posturas) {
    }

    public static class NoPermitido extends RuntimeException {
        private final transient List<Validacion.Error> errores;

        public NoPermitido(List<Validacion.Error> errores) {
            super(errores.stream().map(Validacion.Error::mensaje).collect(Collectors.joining(" ")));
            this.errores = List.copyOf(errores);
        }

        public List<Validacion.Error> errores() {
            return errores;
        }
    }

    private final RegistroDeRazonamiento registro;
    private final GuardadoDeEjecuciones guardado;
    private final Reloj reloj;
    private final EjecutorCambiosOpinion t46 = new EjecutorCambiosOpinion();

    public RegistroDeCambios(RegistroDeRazonamiento registro, GuardadoDeEjecuciones guardado, Reloj reloj) {
        this.registro = registro;
        this.guardado = guardado;
        this.reloj = reloj;
    }

    private LocalDate dia(Instant instante) {
        return instante.atZone(reloj.zona()).toLocalDate();
    }

    /** Los cambios de la persona, en el orden en que se guardaron, como filas de T46. */
    public List<EjecutorCambiosOpinion.Cambio> cambios(UUID usuarioId) {
        return registro.cambios(usuarioId).stream().map(c -> new EjecutorCambiosOpinion.Cambio(dia(c.cambio().creadoEn()).toString(),
                c.cambio().texto(), c.cambio().confianzaAntes(), c.cambio().confianzaDespues(), c.cambio().causa(),
                c.tecnica().map(t -> t.valor()).orElse(EjecutorCambiosOpinion.ID.valor()))).toList();
    }

    /** Las posturas de la persona con la fecha de la última ejecución que las usó, como filas de T46. */
    public List<EjecutorCambiosOpinion.Postura> posturas(UUID usuarioId) {
        return registro.posturas(usuarioId).stream()
                .map(p -> new EjecutorCambiosOpinion.Postura(p.texto(), dia(p.ultimaVez()).toString())).toList();
    }

    public Registro registro(UUID usuarioId, EjecutorCambiosOpinion.Config config) {
        List<EjecutorCambiosOpinion.Postura> posturas = posturas(usuarioId);
        ResultadoCambiosOpinion r = EjecutorCambiosOpinion.calcular(config, cambios(usuarioId), posturas, reloj.hoy());
        List<String> textos = posturas.stream().map(EjecutorCambiosOpinion.Postura::postura).distinct().toList();
        return new Registro(r, textos);
    }

    /** Las ejecuciones de la ventana de T45, de la más vieja a la más nueva, como filas de T45. */
    public List<EjecutorDiarioRazonamiento.Registro> ejecuciones(UUID usuarioId, EjecutorDiarioRazonamiento.Config config) {
        LocalDate inicio = reloj.hoy().with(DayOfWeek.MONDAY).minusWeeks(config.semanas() - 1L);
        Instant desde = inicio.atStartOfDay(reloj.zona()).toInstant();
        return registro.ejecucionesDesde(usuarioId, desde).stream().map(e -> new EjecutorDiarioRazonamiento.Registro(dia(e.creadaEn()).toString(),
                e.tecnica().valor(), e.resumen(), e.expediente().orElse(null), e.cambios())).toList();
    }

    /** El diario de razonamiento con las ejecuciones guardadas; vacío si no hay ninguna en la ventana. */
    public Optional<ResultadoDiarioRazonamiento> diario(UUID usuarioId, EjecutorDiarioRazonamiento.Config config) {
        List<EjecutorDiarioRazonamiento.Registro> registros = ejecuciones(usuarioId, config);
        return registros.isEmpty() ? Optional.empty() : Optional.of(EjecutorDiarioRazonamiento.calcular(config, registros, reloj.hoy()));
    }

    /**
     * Registra a mano un cambio de opinión: una ejecución de T46 con solo ese cambio, que produce la postura y declara el cambio
     * con su causa. La clave evita el doble clic.
     */
    public Ejecucion registrarAMano(UUID usuarioId, UUID institucionId, EjecutorCambiosOpinion.Config config, String postura, Integer antes,
                                    Integer despues, CambioOpinion.Causa causa, String clave) {
        EjecutorCambiosOpinion.Entrada entrada = new EjecutorCambiosOpinion.Entrada(List.of(), List.of(), postura, antes, despues, causa);
        Validacion v = t46.validar(config, entrada);
        if (postura == null || postura.isBlank()) {
            throw new NoPermitido(List.of(new Validacion.Error("nuevaPostura", "Escribe la postura que cambió.")));
        }
        if (!v.esValida()) {
            throw new NoPermitido(v.errores());
        }
        Instant ahora = reloj.ahora();
        Contexto ctx = new Contexto(usuarioId, institucionId, Optional.empty(), reloj, Optional.empty(), () -> Uuid7.en(reloj.ahora()));
        Resultado<ResultadoCambiosOpinion> r = t46.ejecutar(config, entrada, ctx);
        Ejecucion nueva = new Ejecucion(Uuid7.en(ahora), usuarioId, institucionId, EjecutorCambiosOpinion.ID, EjecutorCambiosOpinion.VERSION_ESQUEMA,
                Optional.empty(), MapeadorJson.escribir(config), MapeadorJson.escribir(entrada), MapeadorJson.escribir(r.valor()), r.resumen(),
                Optional.empty(), "registro-" + clave, ahora);
        return guardado.guardar(nueva, r);
    }
}
