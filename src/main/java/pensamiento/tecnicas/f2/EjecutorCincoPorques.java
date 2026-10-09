package pensamiento.tecnicas.f2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 * T09 · 5 porqués (Toyoda y Ohno, años 50). Una cadena de "¿por qué?" sobre un problema, con ramas si la configuración
 * las permite, hasta la causa raíz y con la evidencia de cada nivel. No usa el modelo. Las reglas están en
 * docs/ejemplos/T09.md.
 */
@Component
public class EjecutorCincoPorques implements Ejecutor<EjecutorCincoPorques.Config, EjecutorCincoPorques.Entrada, ResultadoCincoPorques> {

    public static final IdTecnica ID = IdTecnica.de("T09");
    public static final int VERSION_ESQUEMA = 1;
    public static final String PROBLEMA = "problema";

    /** Configuración de T09, versión de esquema 1. */
    public record Config(int niveles, boolean exigirEvidencia, boolean permitirRamas) {
    }

    /**
     * @param respondeA vacío (la fila anterior), el código de una fila anterior o "problema"
     * @param raiz      "aquí paro: es la causa raíz"
     */
    public record Porque(String texto, String evidencia, String respondeA, boolean raiz) {
    }

    public record Entrada(String problema, List<Porque> porques) {
        public Entrada {
            porques = porques == null ? List.of() : List.copyOf(porques);
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
    public Tipos<Config, Entrada, ResultadoCincoPorques> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoCincoPorques.class);
    }

    static String codigo(int i) {
        return "P" + (i + 1);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.niveles() < 3 || config.niveles() > 7) {
            errores.add(new Validacion.Error("config.niveles", "Los niveles van de 3 a 7."));
        }
        if (Textos.vacio(entrada.problema())) {
            errores.add(new Validacion.Error("problema", "Escribe el problema."));
        }
        if (entrada.porques().isEmpty()) {
            errores.add(new Validacion.Error("porques", "Escribe al menos un porqué."));
        }
        Map<String, Integer> niveles = new HashMap<>();
        for (int i = 0; i < entrada.porques().size(); i++) {
            Porque p = entrada.porques().get(i);
            String cod = codigo(i);
            if (Textos.vacio(p.texto())) {
                errores.add(new Validacion.Error("porques", cod + " necesita su texto."));
                continue;
            }
            String a = Textos.vacio(p.respondeA()) ? null : p.respondeA().strip();
            if (a != null && !config.permitirRamas()) {
                errores.add(new Validacion.Error("porques", "Las ramas están apagadas en la configuración: deja vacío «responde a» en " + cod + "."));
                continue;
            }
            int nivelPadre;
            if (a == null) {
                nivelPadre = i == 0 ? 0 : niveles.getOrDefault(codigo(i - 1), 0);
            } else if (PROBLEMA.equalsIgnoreCase(a)) {
                nivelPadre = 0;
            } else if (niveles.containsKey(a.toUpperCase())) {
                nivelPadre = niveles.get(a.toUpperCase());
            } else {
                errores.add(new Validacion.Error("porques", cod + " responde a «" + a + "», que no es un porqué anterior ni «problema»."));
                continue;
            }
            int nivel = nivelPadre + 1;
            if (nivel > config.niveles()) {
                errores.add(new Validacion.Error("porques", cod + " queda en el nivel " + nivel + " y la configuración permite " + config.niveles()
                        + ": sube los niveles o quítalo."));
            }
            niveles.put(cod, nivel);
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoCincoPorques> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        int n = entrada.porques().size();
        String[] padres = new String[n];
        int[] niveles = new int[n];
        Set<String> conHijos = new HashSet<>();
        Map<String, Integer> nivelPorCodigo = new HashMap<>();
        for (int i = 0; i < n; i++) {
            Porque p = entrada.porques().get(i);
            String a = Textos.vacio(p.respondeA()) ? null : p.respondeA().strip();
            String padre = a == null ? (i == 0 ? null : codigo(i - 1)) : PROBLEMA.equalsIgnoreCase(a) ? null : a.toUpperCase();
            padres[i] = padre;
            niveles[i] = (padre == null ? 0 : nivelPorCodigo.get(padre)) + 1;
            nivelPorCodigo.put(codigo(i), niveles[i]);
            if (padre != null) {
                conHijos.add(padre);
            }
        }
        String problema = entrada.problema().strip();
        UUID problemaId = ctx.nuevoId().get();
        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        afirmaciones.add(new AfirmacionConRol(problemaId, problema, TipoAfirmacion.HECHO, RolAfirmacion.PREMISA, SentidoAfirmacion.PRODUCIDA,
                OrigenAfirmacion.USUARIO));
        List<ResultadoCincoPorques.Porque> porques = new ArrayList<>();
        List<Pendiente> pendientes = new ArrayList<>();
        List<Pendiente> pendientesSinTerminar = new ArrayList<>();
        List<String> sinEvidencia = new ArrayList<>();
        int conEvidencia = 0;
        int raices = 0;
        int sinTerminar = 0;
        for (int i = 0; i < n; i++) {
            Porque p = entrada.porques().get(i);
            String cod = codigo(i);
            String texto = p.texto().strip();
            String evidencia = Textos.vacio(p.evidencia()) ? null : p.evidencia().strip();
            if (evidencia != null) {
                conEvidencia++;
            } else {
                sinEvidencia.add(cod);
            }
            ResultadoCincoPorques.Estado estado = ResultadoCincoPorques.Estado.INTERMEDIO;
            if (!conHijos.contains(cod)) {
                estado = niveles[i] == config.niveles() || p.raiz() ? ResultadoCincoPorques.Estado.CAUSA_RAIZ : ResultadoCincoPorques.Estado.SIN_TERMINAR;
            }
            UUID id = ctx.nuevoId().get();
            boolean esRaiz = estado == ResultadoCincoPorques.Estado.CAUSA_RAIZ;
            afirmaciones.add(new AfirmacionConRol(id, texto, TipoAfirmacion.CAUSAL, esRaiz ? RolAfirmacion.CONCLUSION : RolAfirmacion.HIPOTESIS,
                    SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
            if (esRaiz) {
                raices++;
                pendientes.add(new Pendiente(TipoPendiente.VERIFICACION, Optional.of(id), Optional.empty(), "Verificar la causa raíz: " + texto));
            } else if (estado == ResultadoCincoPorques.Estado.SIN_TERMINAR) {
                sinTerminar++;
                pendientesSinTerminar.add(new Pendiente(TipoPendiente.REVISION, Optional.of(id), Optional.empty(), "Seguir preguntando por qué: " + texto));
            }
            porques.add(new ResultadoCincoPorques.Porque(cod, texto, evidencia, padres[i], niveles[i], estado, id));
        }
        pendientes.addAll(pendientesSinTerminar);
        List<String> avisos = new ArrayList<>();
        if (config.exigirEvidencia() && !sinEvidencia.isEmpty()) {
            avisos.add("Falta evidencia en " + Textos.enumerar(sinEvidencia) + ": la configuración la exige.");
        }
        String resumen = Textos.contar(n, "porqué", "porqués") + " · " + Textos.contar(raices, "causa raíz", "causas raíz")
                + (sinTerminar > 0 ? " · " + sinTerminar + " sin terminar" : "") + " · evidencia en " + conEvidencia + " de " + n + ".";
        ResultadoCincoPorques valor = new ResultadoCincoPorques(problema, problemaId, porques, config.niveles(), conEvidencia, raices, sinTerminar, avisos,
                resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen);
    }

    @Override
    public ResultadoCincoPorques migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA + "; se pidió migrar desde la " + desdeVersion);
    }
}
