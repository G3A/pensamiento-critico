package pensamiento.web;

import java.util.UUID;

import org.springframework.stereotype.Component;

import pensamiento.catalogo.MapeadorJson;
import pensamiento.flujos.DojoDeRazonamiento;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Tecnica;
import pensamiento.nucleo.puertos.RepositorioTecnica;
import pensamiento.tecnicas.f8.EjecutorBloom;
import pensamiento.tecnicas.f8.EjecutorCambiosOpinion;
import pensamiento.tecnicas.f8.EjecutorDiarioRazonamiento;
import pensamiento.tecnicas.f8.EjecutorReflexion;
import pensamiento.tecnicas.f8.EjecutorRepeticion;
import pensamiento.web.tecnicas.MotorTecnicas;

/**
 * La configuración vigente de la persona en las técnicas de F8, tipada: la que guardó en la ficha o, si no, la del catálogo.
 * El Dojo usa la de T48 y T49; el registro de cambios (P20), la de T45 y T46; el cierre del Consejero, la de T47.
 */
@Component
public class ConfiguracionesF8 {

    private final MotorTecnicas motor;
    private final RepositorioTecnica tecnicas;

    public ConfiguracionesF8(MotorTecnicas motor, RepositorioTecnica tecnicas) {
        this.motor = motor;
        this.tecnicas = tecnicas;
    }

    private <C> C de(UUID usuarioId, IdTecnica id, Class<C> tipo) {
        Tecnica t = tecnicas.porId(id).orElseThrow(() -> new IllegalStateException(id + " no está en el catálogo"));
        return MapeadorJson.mapper().convertValue(motor.configDeUsuario(usuarioId, t), tipo);
    }

    public DojoDeRazonamiento.Configuracion dojo(UUID usuarioId) {
        return new DojoDeRazonamiento.Configuracion(de(usuarioId, EjecutorBloom.ID, EjecutorBloom.Config.class),
                de(usuarioId, EjecutorRepeticion.ID, EjecutorRepeticion.Config.class));
    }

    public EjecutorDiarioRazonamiento.Config diario(UUID usuarioId) {
        return de(usuarioId, EjecutorDiarioRazonamiento.ID, EjecutorDiarioRazonamiento.Config.class);
    }

    public EjecutorCambiosOpinion.Config cambios(UUID usuarioId) {
        return de(usuarioId, EjecutorCambiosOpinion.ID, EjecutorCambiosOpinion.Config.class);
    }

    public EjecutorReflexion.Config reflexion(UUID usuarioId) {
        return de(usuarioId, EjecutorReflexion.ID, EjecutorReflexion.Config.class);
    }
}
