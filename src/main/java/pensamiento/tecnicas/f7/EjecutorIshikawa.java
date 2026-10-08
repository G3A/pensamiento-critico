package pensamiento.tecnicas.f7;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.Validacion;
import pensamiento.tecnicas.comun.Textos;

/**
 * T43 · Diagrama de Ishikawa (Ishikawa 1968). Agrupa las causas de un efecto por categoría (las 6M o las propias) y
 * señala las categorías vacías o con causas de menos. No usa IA. Las reglas están en docs/ejemplos/T43.md.
 */
@Component
public class EjecutorIshikawa implements Ejecutor<EjecutorIshikawa.Config, EjecutorIshikawa.Entrada, ResultadoIshikawa> {

    public static final IdTecnica ID = IdTecnica.de("T43");
    public static final int VERSION_ESQUEMA = 1;
    public static final String SEIS_M = "Máquina, Método, Material, Personas, Medición, Entorno";
    static final int TOPE_CAUSAS = 24;

    /** @param categorias separadas por comas, de 2 a 8 */
    public record Config(String categorias, int causasMinimas) {
    }

    public record Causa(String texto, String categoria) {
    }

    public record Entrada(String efecto, List<Causa> causas) {
        public Entrada {
            causas = causas == null ? List.of() : List.copyOf(causas);
        }
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
    public Tipos<Config, Entrada, ResultadoIshikawa> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoIshikawa.class);
    }

    /** Las categorías de la configuración, en su orden, sin vacías ni repetidas. */
    public static List<String> categorias(Config config) {
        return config.categorias() == null ? List.of() : Textos.partesPorComa(config.categorias());
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        List<String> categorias = categorias(config);
        if (categorias.size() < 2 || categorias.size() > 8) {
            errores.add(new Validacion.Error("config.categorias", "Escribe de 2 a 8 categorías separadas por comas."));
        }
        if (config.causasMinimas() < 0 || config.causasMinimas() > 5) {
            errores.add(new Validacion.Error("config.causasMinimas", "Las causas mínimas por categoría van de 0 a 5."));
        }
        if (Textos.vacio(entrada.efecto())) {
            errores.add(new Validacion.Error("efecto", "Escribe el efecto: el problema que quieres explicar."));
        }
        if (entrada.causas().isEmpty()) {
            errores.add(new Validacion.Error("causas", "Escribe al menos una causa."));
        } else if (entrada.causas().size() > TOPE_CAUSAS) {
            errores.add(new Validacion.Error("causas", "Caben como máximo " + TOPE_CAUSAS + " causas."));
        }
        for (int i = 0; i < entrada.causas().size(); i++) {
            Causa c = entrada.causas().get(i);
            if (Textos.vacio(c.texto())) {
                errores.add(new Validacion.Error("causas[" + i + "].texto", "Escribe la causa " + (i + 1) + "."));
            }
            if (Textos.vacio(c.categoria()) || !categorias.contains(c.categoria().strip())) {
                errores.add(new Validacion.Error("causas[" + i + "].categoria", "Elige la categoría de la causa " + (i + 1) + "."));
            }
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoIshikawa> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        UUID efectoId = ctx.nuevoId().get();
        String efecto = entrada.efecto().strip();
        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        afirmaciones.add(new AfirmacionConRol(efectoId, efecto, TipoAfirmacion.HECHO, RolAfirmacion.CONCLUSION, SentidoAfirmacion.PRODUCIDA,
                OrigenAfirmacion.USUARIO));
        List<UUID> ids = new ArrayList<>();
        for (Causa c : entrada.causas()) {
            UUID id = ctx.nuevoId().get();
            ids.add(id);
            afirmaciones.add(new AfirmacionConRol(id, c.texto().strip(), TipoAfirmacion.CAUSAL, RolAfirmacion.HIPOTESIS, SentidoAfirmacion.PRODUCIDA,
                    OrigenAfirmacion.USUARIO));
        }
        List<ResultadoIshikawa.Categoria> categorias = new ArrayList<>();
        int conCausas = 0;
        int vacias = 0;
        int cortas = 0;
        for (String nombre : categorias(config)) {
            List<ResultadoIshikawa.CausaEn> suyas = new ArrayList<>();
            for (int i = 0; i < entrada.causas().size(); i++) {
                if (entrada.causas().get(i).categoria().strip().equals(nombre)) {
                    suyas.add(new ResultadoIshikawa.CausaEn(ids.get(i), entrada.causas().get(i).texto().strip()));
                }
            }
            int faltan = Math.max(0, config.causasMinimas() - suyas.size());
            if (suyas.isEmpty()) {
                vacias++;
            } else {
                conCausas++;
                if (faltan > 0) {
                    cortas++;
                }
            }
            categorias.add(new ResultadoIshikawa.Categoria(nombre, suyas, suyas.isEmpty() ? 0 : faltan));
        }
        String resumen = Textos.contar(entrada.causas().size(), "causa", "causas") + " en " + conCausas + " de " + categorias.size() + " categorías";
        if (vacias > 0) {
            resumen += " · " + Textos.contar(vacias, "categoría vacía", "categorías vacías");
        }
        if (cortas > 0) {
            resumen += " · " + cortas + " con menos de " + Textos.contar(config.causasMinimas(), "causa", "causas");
        }
        resumen += ".";
        ResultadoIshikawa valor = new ResultadoIshikawa(efectoId, efecto, categorias, config.causasMinimas(), resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, List.of(), resumen);
    }

    @Override
    public ResultadoIshikawa migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }
}
