package pensamiento.web.tecnicas;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import pensamiento.catalogo.MapeadorJson;
import pensamiento.catalogo.RegistroEjecutores;
import pensamiento.nucleo.ConModelo;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.Propuesta;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.Tecnica;
import pensamiento.nucleo.Uuid7;
import pensamiento.nucleo.Validacion;
import pensamiento.nucleo.puertos.Ia;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioConfiguracion;
import pensamiento.web.formulario.Campo;
import pensamiento.web.formulario.EsquemaFormulario;
import pensamiento.web.formulario.LenguajeCampos;

/**
 * Lo común a las 49 técnicas en la capa web: configuración vigente del usuario, validación en dos pasos (JSON
 * Schema derivado del lenguaje de campos y luego las reglas del ejecutor) y ejecución tipada. No guarda nada:
 * evaluar sin guardar no abre transacción.
 */
@Component
public class MotorTecnicas {

    /** Resultado de evaluar: errores por campo, o el resultado del ejecutor con la configuración y la entrada tipadas. */
    public record Evaluacion(Map<String, String> errores, Optional<Resultado<?>> resultado, Json config, Json entrada) {
        public boolean valida() {
            return errores.isEmpty();
        }
    }

    private final RegistroEjecutores ejecutores;
    private final RepositorioConfiguracion configuraciones;
    private final Reloj reloj;
    private final Ia ia;

    public MotorTecnicas(RegistroEjecutores ejecutores, RepositorioConfiguracion configuraciones, Reloj reloj, Ia ia) {
        this.ejecutores = ejecutores;
        this.configuraciones = configuraciones;
        this.reloj = reloj;
        this.ia = ia;
    }

    public Optional<Ejecutor<?, ?, ?>> ejecutor(IdTecnica id) {
        return ejecutores.porId(id);
    }

    public List<Campo> camposConfig(Tecnica t) {
        return LenguajeCampos.campos(t.esquemaConfig());
    }

    public List<Campo> camposEntrada(Tecnica t) {
        return LenguajeCampos.campos(t.esquemaEntrada());
    }

    /** La configuración guardada por el usuario o, si no hay o es de otra versión de esquema, la del catálogo. */
    public Map<String, Object> configDeUsuario(UUID usuarioId, Tecnica t) {
        Map<String, Object> config = LenguajeCampos.mapa(t.configDefault());
        configuraciones.de(usuarioId, t.id())
                .filter(g -> g.versionEsquema() == t.versionEsquema())
                .ifPresent(g -> config.putAll(LenguajeCampos.mapa(g.valores())));
        return config;
    }

    public boolean configPersonalizada(UUID usuarioId, Tecnica t) {
        return configuraciones.de(usuarioId, t.id()).filter(g -> g.versionEsquema() == t.versionEsquema()).isPresent();
    }

    /** Valida la configuración contra su propio esquema; errores con la ruta del campo de configuración. */
    public Map<String, String> validarConfig(Tecnica t, Map<String, Object> config) {
        return EsquemaFormulario.validar(camposConfig(t), config, config);
    }

    public void guardarConfig(UUID usuarioId, UUID institucionId, Tecnica t, Map<String, Object> config) {
        configuraciones.guardar(usuarioId, institucionId, t.id(), t.versionEsquema(), LenguajeCampos.json(config));
    }

    public void restablecerConfig(UUID usuarioId, Tecnica t) {
        configuraciones.restablecer(usuarioId, t.id());
    }

    public Contexto contexto(UUID usuarioId, UUID institucionId) {
        return new Contexto(usuarioId, institucionId, Optional.empty(), reloj, Optional.empty(), () -> Uuid7.en(reloj.ahora()));
    }

    /** Valida y, si todo está bien, ejecuta. Nunca lanza por datos del usuario: los errores vuelven por campo. */
    public Evaluacion evaluar(Tecnica t, Map<String, Object> config, Map<String, Object> entrada, Contexto ctx) {
        Ejecutor<?, ?, ?> ejecutor = ejecutores.porId(t.id())
                .orElseThrow(() -> new IllegalStateException(t.cita() + " no tiene ejecutor"));
        Map<String, String> errores = new LinkedHashMap<>();
        validarConfig(t, config).forEach((campo, mensaje) -> errores.put("config." + campo, mensaje));
        errores.putAll(EsquemaFormulario.validar(camposEntrada(t), config, entrada));
        if (!errores.isEmpty()) {
            return new Evaluacion(errores, Optional.empty(), LenguajeCampos.json(config), LenguajeCampos.json(entrada));
        }
        return correr(ejecutor, config, entrada, ctx);
    }

    private <C, E, R> Evaluacion correr(Ejecutor<C, E, R> ejecutor, Map<String, Object> config, Map<String, Object> entrada, Contexto ctx) {
        C c = MapeadorJson.mapper().convertValue(config, ejecutor.tipos().config());
        E e = MapeadorJson.mapper().convertValue(entrada, ejecutor.tipos().entrada());
        Validacion validacion = ejecutor.validar(c, e);
        if (!validacion.esValida()) {
            Map<String, String> errores = new LinkedHashMap<>();
            validacion.errores().forEach(err -> errores.putIfAbsent(err.campo(), err.mensaje()));
            return new Evaluacion(errores, Optional.empty(), MapeadorJson.escribir(c), MapeadorJson.escribir(e));
        }
        Resultado<R> resultado = ejecutor.ejecutar(c, e, ctx);
        return new Evaluacion(Map.of(), Optional.of(resultado), MapeadorJson.escribir(c), MapeadorJson.escribir(e));
    }

    // ---------------------------------------------------------------------------------------------
    // El modelo propone (RF-14)
    // ---------------------------------------------------------------------------------------------

    /** El ejecutor como técnica con Ollama opcional, si lo es. */
    public Optional<ConModelo<?, ?>> conModelo(IdTecnica id) {
        return ejecutores.porId(id).filter(e -> e instanceof ConModelo<?, ?>).map(e -> (ConModelo<?, ?>) e);
    }

    /** Si la técnica tiene modelo y la configuración vigente lo pide. */
    public boolean pideModelo(Tecnica t, Map<String, Object> config) {
        return conModelo(t.id()).map(m -> usa(m, t, config)).orElse(false);
    }

    /** Si el modelo local responde ahora mismo (el cortacircuitos decide; no consulta la red). */
    public boolean modeloDisponible() {
        return ia.estado().disponible();
    }

    /** Contexto con la IA solo si el cortacircuitos dice que está: así cada llamada decide por sí misma. */
    public Contexto contextoConIa(UUID usuarioId, UUID institucionId) {
        return new Contexto(usuarioId, institucionId, Optional.empty(), reloj, modeloDisponible() ? Optional.of(ia) : Optional.empty(),
                () -> Uuid7.en(reloj.ahora()));
    }

    /** Lo que vuelve de pedir propuestas: los valores del formulario con las propuestas nuevas, o la caída. */
    public record ConPropuestas(Map<String, Object> valores, int nuevas, Optional<String> caida) {
    }

    /**
     * Pide propuestas al modelo y las agrega a los valores: las adoptadas se quedan, las que no se habían adoptado se
     * reemplazan por las nuevas, y los códigos siguen la numeración (nunca se reusa un IA).
     */
    public ConPropuestas proponer(Tecnica t, Map<String, Object> config, Map<String, Object> valores, Contexto ctx,
                                  java.util.function.Consumer<String> provisional) {
        Ejecutor<?, ?, ?> ejecutor = ejecutores.porId(t.id()).orElseThrow(() -> new IllegalStateException(t.cita() + " no tiene ejecutor"));
        if (!(ejecutor instanceof ConModelo<?, ?>)) {
            return new ConPropuestas(valores, 0, Optional.of("Esta técnica no usa el modelo local."));
        }
        return proponerCon(ejecutor, config, valores, ctx, provisional);
    }

    @SuppressWarnings("unchecked")
    private <C, E, R> ConPropuestas proponerCon(Ejecutor<C, E, R> ejecutor, Map<String, Object> config, Map<String, Object> valores, Contexto ctx,
                                                java.util.function.Consumer<String> provisional) {
        ConModelo<C, E> modelo = (ConModelo<C, E>) ejecutor;
        C c = MapeadorJson.mapper().convertValue(config, ejecutor.tipos().config());
        E e = MapeadorJson.mapper().convertValue(valores, ejecutor.tipos().entrada());
        List<Propuesta> existentes = modelo.propuestas(e);
        ConModelo.Propuestas propuestas = modelo.proponer(c, e, ctx, provisional, Propuesta.siguiente(existentes));
        if (propuestas.caida().isPresent()) {
            return new ConPropuestas(valores, 0, propuestas.caida());
        }
        List<Object> todas = new java.util.ArrayList<>();
        existentes.stream().filter(Propuesta::adoptada).forEach(p -> todas.add(MapeadorJson.mapper().convertValue(p, Map.class)));
        propuestas.nuevas().forEach(p -> todas.add(MapeadorJson.mapper().convertValue(p, Map.class)));
        Map<String, Object> nuevos = new LinkedHashMap<>(valores);
        nuevos.put("propuestas", todas);
        return new ConPropuestas(nuevos, propuestas.nuevas().size(), Optional.empty());
    }

    /** Adopta una propuesta: la técnica la lleva a su lugar en la entrada con origen modelo. Vacío si el código no sirve. */
    public Optional<Map<String, Object>> adoptar(Tecnica t, Map<String, Object> config, Map<String, Object> valores, String codigo) {
        return ejecutores.porId(t.id()).filter(e -> e instanceof ConModelo<?, ?>).flatMap(e -> adoptarCon(e, valores, codigo));
    }

    @SuppressWarnings("unchecked")
    private <C, E, R> Optional<Map<String, Object>> adoptarCon(Ejecutor<C, E, R> ejecutor, Map<String, Object> valores, String codigo) {
        ConModelo<C, E> modelo = (ConModelo<C, E>) ejecutor;
        E e = MapeadorJson.mapper().convertValue(valores, ejecutor.tipos().entrada());
        try {
            E adoptada = modelo.adoptar(e, codigo);
            return Optional.of(MapeadorJson.mapper().convertValue(adoptada, LinkedHashMap.class));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private <C, E> boolean usa(ConModelo<C, E> modelo, Tecnica t, Map<String, Object> config) {
        Ejecutor<C, E, ?> ejecutor = (Ejecutor<C, E, ?>) modelo;
        try {
            return modelo.usaModelo(MapeadorJson.mapper().convertValue(config, ejecutor.tipos().config()));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** El valor guardado de una ejecución, leído con la versión vigente o migrado de forma perezosa. */
    public Object valorDe(Ejecucion ejecucion) {
        Ejecutor<?, ?, ?> ejecutor = ejecutores.porId(ejecucion.tecnica())
                .orElseThrow(() -> new IllegalStateException(ejecucion.tecnica() + " no tiene ejecutor"));
        if (ejecucion.versionEsquema() < ejecutor.versionEsquema()) {
            return ejecutor.migrar(ejecucion.resultado(), ejecucion.versionEsquema());
        }
        return MapeadorJson.leer(ejecucion.resultado(), ejecutor.tipos().resultado());
    }
}
