package pensamiento.web.tecnicas;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import pensamiento.catalogo.MapeadorJson;
import pensamiento.flujos.DojoDeRazonamiento;
import pensamiento.flujos.RegistroDeCambios;
import pensamiento.nucleo.BancoDojo;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.IntentoDojo;
import pensamiento.tecnicas.f8.EjecutorBloom;
import pensamiento.tecnicas.f8.EjecutorCambiosOpinion;
import pensamiento.tecnicas.f8.EjecutorDiarioRazonamiento;
import pensamiento.tecnicas.f8.EjecutorRepeticion;
import pensamiento.tecnicas.f8.TemaDojo;
import pensamiento.web.ConfiguracionesF8;

/**
 * "Usar mis datos" en las fichas de T45 · Diario de razonamiento, T46 · Registro de cambios de opinión, T48 · Taxonomía de
 * Bloom y T49 · Repetición espaciada: llena el formulario con lo que la persona ya guardó (ejecuciones, cambios de opinión,
 * posturas e intentos del Dojo), como mucho las 60 filas que caben en el formulario, las más recientes.
 */
@Component
public class DatosPropios {

    static final int FILAS = 60;

    private final RegistroDeCambios registro;
    private final DojoDeRazonamiento dojo;
    private final ConfiguracionesF8 configuraciones;

    public DatosPropios(RegistroDeCambios registro, DojoDeRazonamiento dojo, ConfiguracionesF8 configuraciones) {
        this.registro = registro;
        this.dojo = dojo;
        this.configuraciones = configuraciones;
    }

    /** Qué trae "Usar mis datos" en esta técnica; vacío si la técnica no lo ofrece. */
    public Optional<String> descripcion(IdTecnica tecnica) {
        return switch (tecnica.valor()) {
            case "T45" -> Optional.of("tus ejecuciones guardadas de las semanas que mira el diario");
            case "T46" -> Optional.of("tus cambios de opinión y tus posturas");
            case "T48" -> Optional.of("tus intentos del Dojo en el tema que practicaste por última vez");
            case "T49" -> Optional.of("tus intentos del Dojo");
            default -> Optional.empty();
        };
    }

    /** La entrada con los datos de la persona, como valores del formulario. */
    public Map<String, Object> entrada(IdTecnica tecnica, UUID usuarioId) {
        Object entrada = switch (tecnica.valor()) {
            case "T45" -> new EjecutorDiarioRazonamiento.Entrada(ultimas(registro.ejecuciones(usuarioId, configuraciones.diario(usuarioId))));
            case "T46" -> new EjecutorCambiosOpinion.Entrada(ultimas(registro.cambios(usuarioId)), primeras(registro.posturas(usuarioId)), null, null,
                    null, null);
            case "T48" -> bloom(dojo.intentos(usuarioId));
            case "T49" -> repeticion(dojo.intentos(usuarioId));
            default -> throw new IllegalArgumentException(tecnica + " no ofrece usar tus datos");
        };
        @SuppressWarnings("unchecked")
        Map<String, Object> valores = MapeadorJson.mapper().convertValue(entrada, LinkedHashMap.class);
        return valores;
    }

    private static EjecutorBloom.Entrada bloom(List<IntentoDojo> intentos) {
        TemaDojo tema = intentos.isEmpty() ? TemaDojo.FALACIAS : TemaDojo.de(intentos.getLast().tecnica());
        return new EjecutorBloom.Entrada(tema, ultimas(intentos.stream().filter(i -> i.tecnica().equals(tema.tecnica()))
                .map(i -> new EjecutorBloom.Intento(i.nivel(), i.acierto() ? EjecutorBloom.ResultadoIntento.ACIERTO : EjecutorBloom.ResultadoIntento.ERROR))
                .toList()));
    }

    private EjecutorRepeticion.Entrada repeticion(List<IntentoDojo> intentos) {
        BancoDojo banco = dojo.banco();
        return new EjecutorRepeticion.Entrada(ultimas(intentos.stream().map(i -> new EjecutorRepeticion.Repaso(TemaDojo.de(i.tecnica()),
                banco.concepto(i.concepto()).map(BancoDojo.Concepto::nombre).orElse(i.concepto()), i.dia().toString(),
                i.acierto() ? EjecutorBloom.ResultadoIntento.ACIERTO : EjecutorBloom.ResultadoIntento.ERROR)).toList()));
    }

    /** Las últimas filas que caben, en su orden. */
    private static <T> List<T> ultimas(List<T> filas) {
        return filas.size() <= FILAS ? filas : filas.subList(filas.size() - FILAS, filas.size());
    }

    /** Las posturas que llevan más tiempo sin tocarse, que son las que importan para avisar. */
    private static List<EjecutorCambiosOpinion.Postura> primeras(List<EjecutorCambiosOpinion.Postura> posturas) {
        return posturas.stream().sorted(Comparator.comparing(EjecutorCambiosOpinion.Postura::desde)).limit(FILAS).toList();
    }
}
