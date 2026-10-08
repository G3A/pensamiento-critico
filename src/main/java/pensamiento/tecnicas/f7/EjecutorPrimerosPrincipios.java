package pensamiento.tecnicas.f7;

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
 * T41 · Primeros principios (Aristóteles; uso moderno). Separa lo que se sabe con certeza de lo que se asume y deja cada
 * supuesto como pendiente de verificación. No usa IA. Las reglas están en docs/ejemplos/T41.md.
 */
@Component
public class EjecutorPrimerosPrincipios
        implements Ejecutor<EjecutorPrimerosPrincipios.Config, EjecutorPrimerosPrincipios.Entrada, ResultadoPrimerosPrincipios> {

    public static final IdTecnica ID = IdTecnica.de("T41");
    public static final int VERSION_ESQUEMA = 1;
    static final int TOPE = 8;

    /** Configuración de T41, versión de esquema 1. */
    public record Config(int certezasMinimas, int supuestosMinimos, boolean exigirVerificable) {
    }

    /** @param como cómo lo sé (certeza) o cómo lo verificaría (supuesto); opcional */
    public record Fila(String texto, String como) {
    }

    public record Entrada(String problema, List<Fila> certezas, List<Fila> supuestos) {
        public Entrada {
            certezas = certezas == null ? List.of() : List.copyOf(certezas);
            supuestos = supuestos == null ? List.of() : List.copyOf(supuestos);
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
    public Tipos<Config, Entrada, ResultadoPrimerosPrincipios> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoPrimerosPrincipios.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.certezasMinimas() < 1 || config.certezasMinimas() > TOPE || config.supuestosMinimos() < 1 || config.supuestosMinimos() > TOPE) {
            errores.add(new Validacion.Error("config.certezasMinimas", "Los mínimos van de 1 a " + TOPE + "."));
        }
        if (Textos.vacio(entrada.problema())) {
            errores.add(new Validacion.Error("problema", "Escribe el problema."));
        }
        filas(errores, "certezas", entrada.certezas(), "Escribe al menos una cosa que sepas con certeza.", "Escribe la certeza C");
        filas(errores, "supuestos", entrada.supuestos(), "Escribe al menos un supuesto: algo que das por sentado.", "Escribe el supuesto S");
        return new Validacion(errores);
    }

    private static void filas(List<Validacion.Error> errores, String campo, List<Fila> filas, String vacia, String sinTexto) {
        if (filas.isEmpty()) {
            errores.add(new Validacion.Error(campo, vacia));
        } else if (filas.size() > TOPE) {
            errores.add(new Validacion.Error(campo, "Caben como máximo " + TOPE + "."));
        }
        for (int i = 0; i < filas.size(); i++) {
            if (Textos.vacio(filas.get(i).texto())) {
                errores.add(new Validacion.Error(campo + "[" + i + "].texto", sinTexto + (i + 1) + "."));
            }
        }
    }

    @Override
    public Resultado<ResultadoPrimerosPrincipios> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        List<ResultadoPrimerosPrincipios.Item> certezas = new ArrayList<>();
        for (Fila f : entrada.certezas()) {
            UUID id = ctx.nuevoId().get();
            afirmaciones.add(new AfirmacionConRol(id, f.texto().strip(), TipoAfirmacion.HECHO, RolAfirmacion.PREMISA, SentidoAfirmacion.PRODUCIDA,
                    OrigenAfirmacion.USUARIO));
            certezas.add(new ResultadoPrimerosPrincipios.Item(id, f.texto().strip(), limpio(f.como()), false));
        }
        List<ResultadoPrimerosPrincipios.Item> supuestos = new ArrayList<>();
        List<Pendiente> pendientes = new ArrayList<>();
        int sinComo = 0;
        for (Fila f : entrada.supuestos()) {
            UUID id = ctx.nuevoId().get();
            String texto = f.texto().strip();
            afirmaciones.add(new AfirmacionConRol(id, texto, TipoAfirmacion.HECHO, RolAfirmacion.SUPUESTO, SentidoAfirmacion.PRODUCIDA,
                    OrigenAfirmacion.USUARIO));
            boolean falta = config.exigirVerificable() && limpio(f.como()) == null;
            if (falta) {
                sinComo++;
            }
            supuestos.add(new ResultadoPrimerosPrincipios.Item(id, texto, limpio(f.como()), falta));
            pendientes.add(new Pendiente(TipoPendiente.VERIFICACION, Optional.of(id), Optional.empty(), "Verificar el supuesto: " + texto));
        }
        List<String> avisos = new ArrayList<>();
        int faltanCertezas = config.certezasMinimas() - certezas.size();
        if (faltanCertezas > 0) {
            avisos.add((faltanCertezas == 1 ? "Falta " : "Faltan ") + Textos.contar(faltanCertezas, "certeza", "certezas")
                    + ": la configuración pide al menos " + config.certezasMinimas() + ".");
        }
        int faltanSupuestos = config.supuestosMinimos() - supuestos.size();
        if (faltanSupuestos > 0) {
            avisos.add((faltanSupuestos == 1 ? "Falta " : "Faltan ") + Textos.contar(faltanSupuestos, "supuesto", "supuestos")
                    + ": la configuración pide al menos " + config.supuestosMinimos() + ". ¿Qué das por sentado?");
        }
        if (sinComo > 0) {
            avisos.add("Falta cómo verificar " + Textos.contar(sinComo, "supuesto", "supuestos") + ".");
        }
        String resumen = Textos.contar(certezas.size(), "certeza", "certezas") + " · "
                + Textos.contar(supuestos.size(), "supuesto por verificar", "supuestos por verificar") + ".";
        ResultadoPrimerosPrincipios valor = new ResultadoPrimerosPrincipios(entrada.problema().strip(), certezas, supuestos, avisos, resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen);
    }

    private static String limpio(String texto) {
        return Textos.vacio(texto) ? null : texto.strip();
    }

    @Override
    public ResultadoPrimerosPrincipios migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }
}
