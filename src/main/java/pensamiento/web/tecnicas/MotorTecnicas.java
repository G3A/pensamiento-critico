package pensamiento.web.tecnicas;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import pensamiento.catalogo.MapeadorJson;
import pensamiento.catalogo.RegistroEjecutores;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.Tecnica;
import pensamiento.nucleo.Uuid7;
import pensamiento.nucleo.Validacion;
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

    public MotorTecnicas(RegistroEjecutores ejecutores, RepositorioConfiguracion configuraciones, Reloj reloj) {
        this.ejecutores = ejecutores;
        this.configuraciones = configuraciones;
        this.reloj = reloj;
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
