package pensamiento.tecnicas.f1;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.EstandarPrueba;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.Validacion;
import pensamiento.nucleo.argdown.DocumentoArgdown;
import pensamiento.nucleo.argdown.ErrorSintaxisArgdown;
import pensamiento.nucleo.puertos.Argdown;

/**
 * T01 · Mapeo de argumentos (Wigmore 1913; van Gelder 2003). Lee el texto en el subconjunto Argdown (RF-09),
 * arma el mapa con roles, aplica R04 a cada argumento y dice qué objeciones faltan por responder. Las reglas
 * de cálculo están en docs/ejemplos/T01.md.
 */
@Component
public class EjecutorMapa implements Ejecutor<EjecutorMapa.Config, EjecutorMapa.Entrada, ResultadoMapa> {

    public static final IdTecnica ID = IdTecnica.de("T01");
    public static final int VERSION_ESQUEMA = 1;

    /** Configuración de T01, versión de esquema 1. */
    public record Config(ResultadoMapa.Direccion direccion, boolean coloresPorRol, boolean mostrarPesos, EstandarPrueba estandar) {
    }

    /** El texto del mapa en el subconjunto Argdown. */
    public record Entrada(String argdown) {
    }

    private final Argdown argdown;

    public EjecutorMapa(Argdown argdown) {
        this.argdown = argdown;
    }

    @Override
    public IdTecnica id() {
        return ID;
    }

    @Override
    public int versionEsquema() {
        return VERSION_ESQUEMA;
    }

    @Override
    public Tipos<Config, Entrada, ResultadoMapa> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoMapa.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.direccion() == null) {
            errores.add(new Validacion.Error("direccion", "Elige la dirección del mapa."));
        }
        if (config.estandar() == null) {
            errores.add(new Validacion.Error("estandar", "Elige el estándar de prueba."));
        }
        try {
            argdown.leer(entrada.argdown());
        } catch (ErrorSintaxisArgdown e) {
            errores.add(new Validacion.Error("argdown", e.getMessage()));
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoMapa> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        DocumentoArgdown documento = argdown.leer(entrada.argdown());
        MapaArgumental.Construido mapa = MapaArgumental.construir(documento, argdown.escribir(documento),
                new MapaArgumental.Opciones(config.direccion(), config.coloresPorRol(), config.mostrarPesos(), config.estandar()), ctx.nuevoId());
        return new Resultado<>(VERSION_ESQUEMA, mapa.valor(), mapa.afirmaciones(), mapa.pendientes(), mapa.valor().resumen(), mapa.argumentos());
    }

    @Override
    public ResultadoMapa migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }
}
