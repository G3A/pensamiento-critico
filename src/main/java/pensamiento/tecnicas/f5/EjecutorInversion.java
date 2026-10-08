package pensamiento.tecnicas.f5;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.nucleo.Validacion;
import pensamiento.tecnicas.comun.Textos;

/**
 * T30 · Inversión (Jacobi, siglo XIX; Munger 1986). Lista formas de garantizar el fracaso de una meta y, por cada una,
 * la acción contraria. En el hito 4 no usa el modelo. Las reglas están en docs/ejemplos/T30.md.
 */
@Component
public class EjecutorInversion implements Ejecutor<EjecutorInversion.Config, EjecutorInversion.Entrada, ResultadoInversion> {

    public static final IdTecnica ID = IdTecnica.de("T30");
    public static final int VERSION_ESQUEMA = 1;
    static final int TOPE_FORMAS = 10;

    /** Configuración de T30, versión de esquema 1. */
    public record Config(int formasMinimas, boolean exigirAccionContraria) {
    }

    public record Forma(String texto, String contraria) {
    }

    public record Entrada(String meta, List<Forma> formas) {
        public Entrada {
            formas = formas == null ? List.of() : List.copyOf(formas);
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
    public Tipos<Config, Entrada, ResultadoInversion> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoInversion.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.formasMinimas() < 1 || config.formasMinimas() > TOPE_FORMAS) {
            errores.add(new Validacion.Error("config.formasMinimas", "Las formas mínimas van de 1 a " + TOPE_FORMAS + "."));
        }
        if (Textos.vacio(entrada.meta())) {
            errores.add(new Validacion.Error("meta", "Escribe la meta que quieres proteger."));
        }
        if (entrada.formas().isEmpty()) {
            errores.add(new Validacion.Error("formas", "Escribe al menos una forma de garantizar el fracaso."));
        } else if (entrada.formas().size() > TOPE_FORMAS) {
            errores.add(new Validacion.Error("formas", "Caben como máximo " + TOPE_FORMAS + " formas."));
        }
        for (int i = 0; i < entrada.formas().size(); i++) {
            if (Textos.vacio(entrada.formas().get(i).texto())) {
                errores.add(new Validacion.Error("formas[" + i + "].texto", "Escribe la forma de fracasar " + (i + 1) + "."));
            }
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoInversion> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        String meta = entrada.meta().strip();
        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        afirmaciones.add(new AfirmacionConRol(ctx.nuevoId().get(), meta, TipoAfirmacion.JUICIO_DE_VALOR, RolAfirmacion.OPCION,
                SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
        List<ResultadoInversion.Par> pares = new ArrayList<>();
        List<Pendiente> pendientes = new ArrayList<>();
        int conContraria = 0;
        for (Forma f : entrada.formas()) {
            UUID id = ctx.nuevoId().get();
            String texto = f.texto().strip();
            afirmaciones.add(new AfirmacionConRol(id, texto, TipoAfirmacion.CAUSAL, RolAfirmacion.HIPOTESIS, SentidoAfirmacion.PRODUCIDA,
                    OrigenAfirmacion.USUARIO));
            String contraria = Textos.vacio(f.contraria()) ? null : f.contraria().strip();
            if (contraria != null) {
                conContraria++;
                afirmaciones.add(new AfirmacionConRol(ctx.nuevoId().get(), contraria, TipoAfirmacion.JUICIO_DE_VALOR, RolAfirmacion.OPCION,
                        SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
            } else if (config.exigirAccionContraria()) {
                pendientes.add(new Pendiente(TipoPendiente.REVISION, Optional.of(id), Optional.empty(), "Escribir la acción contraria de: " + texto));
            }
            pares.add(new ResultadoInversion.Par(texto, contraria));
        }
        List<String> avisos = new ArrayList<>();
        List<String> faltas = new ArrayList<>();
        int faltanFormas = config.formasMinimas() - pares.size();
        if (faltanFormas > 0) {
            avisos.add((faltanFormas == 1 ? "Falta " : "Faltan ") + Textos.contar(faltanFormas, "forma", "formas")
                    + " de fracasar: la configuración pide al menos " + config.formasMinimas() + ".");
            faltas.add(Textos.contar(faltanFormas, "forma", "formas"));
        }
        int sinContraria = pares.size() - conContraria;
        if (config.exigirAccionContraria() && sinContraria > 0) {
            faltas.add(Textos.contar(sinContraria, "acción contraria", "acciones contrarias"));
        }
        boolean completa = faltas.isEmpty();
        String estado = completa ? "completa" : "incompleta: " + (faltanFormas > 1 || (faltanFormas <= 0 && sinContraria > 1) ? "faltan " : "falta ")
                + Textos.enumerar(faltas);
        String resumen = Textos.contar(pares.size(), "forma de fracasar", "formas de fracasar") + " · " + conContraria + " con acción contraria.";
        ResultadoInversion valor = new ResultadoInversion(meta, "¿Cómo garantizo que esto fracase? «" + meta + "»", pares, completa, estado,
                config.exigirAccionContraria(), avisos, resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen);
    }

    @Override
    public ResultadoInversion migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }
}
