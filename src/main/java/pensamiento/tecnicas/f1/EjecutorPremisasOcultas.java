package pensamiento.tecnicas.f1;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.Validacion;
import pensamiento.nucleo.argdown.DocumentoArgdown;
import pensamiento.nucleo.argdown.DocumentoArgdown.ArgumentoArgdown;
import pensamiento.nucleo.argdown.DocumentoArgdown.Enunciado;
import pensamiento.nucleo.argdown.DocumentoArgdown.Marca;
import pensamiento.nucleo.argdown.DocumentoArgdown.Relacion;
import pensamiento.nucleo.puertos.Argdown;

/**
 * T06 · Reconstrucción de premisas ocultas (Aristóteles, entimema; Walton 2001). En este hito la persona
 * escribe la premisa oculta; el ejecutor la marca como oculta en el mapa, la guarda como supuesto y deja
 * pendiente verificarla. Las reglas están en docs/ejemplos/T06.md.
 */
@Component
public class EjecutorPremisasOcultas implements Ejecutor<EjecutorPremisasOcultas.Config, EjecutorPremisasOcultas.Entrada, ResultadoMapa> {

    public static final IdTecnica ID = IdTecnica.de("T06");
    public static final int VERSION_ESQUEMA = 1;
    public static final int MAXIMO_PREMISAS = 4;
    public static final int TOPE_OCULTAS = 3;
    private static final int LARGO_TEXTO = 200;
    private static final String NOMBRE_ARGUMENTO = "Argumento";

    /** @param maxOcultas de 1 a 3 */
    public record Config(boolean exigirOculta, int maxOcultas) {
    }

    public record Texto(String texto) {
    }

    public record Entrada(String conclusion, List<Texto> premisas, List<Texto> ocultas) {
        public Entrada {
            premisas = premisas == null ? List.of() : List.copyOf(premisas);
            ocultas = ocultas == null ? List.of() : List.copyOf(ocultas);
        }
    }

    private final Argdown argdown;

    public EjecutorPremisasOcultas(Argdown argdown) {
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
        if (config.maxOcultas() < 1 || config.maxOcultas() > TOPE_OCULTAS) {
            errores.add(new Validacion.Error("maxOcultas", "El máximo de premisas ocultas va de 1 a " + TOPE_OCULTAS + "."));
        }
        texto(errores, "conclusion", entrada.conclusion(), "Escribe la conclusión: lo que el argumento quiere que aceptes.");
        if (entrada.premisas().isEmpty()) {
            errores.add(new Validacion.Error("premisas", "Escribe al menos una premisa explícita."));
        } else if (entrada.premisas().size() > MAXIMO_PREMISAS) {
            errores.add(new Validacion.Error("premisas", "Caben como máximo " + MAXIMO_PREMISAS + " premisas explícitas."));
        }
        for (int i = 0; i < entrada.premisas().size(); i++) {
            texto(errores, "premisas[" + i + "].texto", entrada.premisas().get(i).texto(), "Escribe la premisa " + (i + 1) + ".");
        }
        if (config.exigirOculta() && entrada.ocultas().isEmpty()) {
            errores.add(new Validacion.Error("ocultas",
                    "Escribe al menos una premisa oculta: ¿qué das por sentado para pasar de las premisas a la conclusión?"));
        } else if (entrada.ocultas().size() > config.maxOcultas()) {
            errores.add(new Validacion.Error("ocultas", "Tu configuración admite como máximo " + config.maxOcultas() + " premisas ocultas."));
        }
        for (int i = 0; i < entrada.ocultas().size(); i++) {
            texto(errores, "ocultas[" + i + "].texto", entrada.ocultas().get(i).texto(), "Escribe la premisa oculta " + (i + 1) + ".");
        }
        if (errores.isEmpty()) {
            try {
                argdown.escribir(documento(entrada));
            } catch (IllegalArgumentException e) {
                errores.add(new Validacion.Error("conclusion",
                        "Ningún texto puede empezar con [, <, + ni -, ni terminar en #oculta o #asumible: cambia esas palabras."));
            }
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoMapa> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        DocumentoArgdown documento = documento(entrada);
        MapaArgumental.Construido mapa = MapaArgumental.construir(documento, argdown.escribir(documento), MapaArgumental.Opciones.porDefecto(),
                ctx.nuevoId());
        List<String> ocultas = entrada.ocultas().stream().map(t -> t.texto().strip()).toList();
        String resumen = switch (ocultas.size()) {
            case 0 -> "Ninguna premisa oculta reconstruida.";
            case 1 -> "1 premisa oculta reconstruida: " + Textos.enumerarCitas(ocultas) + ".";
            default -> ocultas.size() + " premisas ocultas reconstruidas: " + Textos.enumerarCitas(ocultas) + ".";
        };
        return new Resultado<>(VERSION_ESQUEMA, mapa.valor().conResumen(resumen), mapa.afirmaciones(), mapa.pendientes(), resumen, mapa.argumentos());
    }

    @Override
    public ResultadoMapa migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }

    /** Un solo argumento con nombre: las explícitas y después las ocultas, marcadas #oculta. */
    private static DocumentoArgdown documento(Entrada entrada) {
        List<Relacion> premisas = new ArrayList<>();
        entrada.premisas().forEach(p -> premisas.add(new Relacion(Relacion.Tipo.APOYO, null, new Enunciado(null, p.texto().strip(), null, List.of()))));
        entrada.ocultas().forEach(p -> premisas.add(new Relacion(Relacion.Tipo.APOYO, null, new Enunciado(null, p.texto().strip(), Marca.OCULTA, List.of()))));
        Relacion apoyo = new Relacion(Relacion.Tipo.APOYO, null, new ArgumentoArgdown(NOMBRE_ARGUMENTO, null, premisas));
        return new DocumentoArgdown(List.of(new Enunciado(null, entrada.conclusion().strip(), null, List.of(apoyo))));
    }

    private static void texto(List<Validacion.Error> errores, String campo, String texto, String mensaje) {
        if (texto == null || texto.isBlank()) {
            errores.add(new Validacion.Error(campo, mensaje));
        } else if (texto.strip().length() > LARGO_TEXTO) {
            errores.add(new Validacion.Error(campo, "Como máximo " + LARGO_TEXTO + " caracteres."));
        }
    }
}
