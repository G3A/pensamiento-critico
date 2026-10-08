package pensamiento.testutil.fakes;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

import pensamiento.nucleo.puertos.Clasificacion;
import pensamiento.nucleo.puertos.EstadoIa;
import pensamiento.nucleo.puertos.Ia;
import pensamiento.nucleo.puertos.IaNoDisponible;
import pensamiento.nucleo.puertos.IaRespuestaInvalida;
import pensamiento.nucleo.puertos.IaTiempoAgotado;
import pensamiento.nucleo.puertos.PeticionChat;
import pensamiento.nucleo.puertos.PeticionClasificacion;
import pensamiento.nucleo.puertos.PeticionEmbeddings;
import pensamiento.nucleo.puertos.RespuestaChat;

/**
 * Fake en memoria del puerto Ia. Se programa con respuestas; reproduce en código lo que el contrato exige:
 * tokens provisionales, reintentos con validador, JSON inválido, etiqueta fuera del enum, no disponible y
 * tiempo agotado. Certificado por FakeIaContractTest.
 */
public final class FakeIa implements Ia {

    private final Deque<String> respuestasChat = new ArrayDeque<>();
    private final Deque<String> respuestasCrudasClasificacion = new ArrayDeque<>();
    private boolean disponible = true;
    private boolean lento = false;
    private int dimension = 1024;
    private final List<PeticionChat> chatsRecibidos = new ArrayList<>();
    private final List<PeticionClasificacion> clasificacionesRecibidas = new ArrayList<>();

    public void programarRespuesta(String texto) {
        respuestasChat.add(texto);
    }

    /** Lo que el modelo "devolvería" como JSON de clasificación (puede ser basura para probar JSON inválido). */
    public void programarClasificacionCruda(String json) {
        respuestasCrudasClasificacion.add(json);
    }

    public void apagar() {
        disponible = false;
    }

    public void encender() {
        disponible = true;
    }

    /** Simula que el modelo tarda más que cualquier tiempo máximo. */
    public void hacerLento() {
        lento = true;
    }

    public void dimensionEmbeddings(int dimension) {
        this.dimension = dimension;
    }

    public List<PeticionChat> chatsRecibidos() {
        return List.copyOf(chatsRecibidos);
    }

    public List<PeticionClasificacion> clasificacionesRecibidas() {
        return List.copyOf(clasificacionesRecibidas);
    }

    @Override
    public EstadoIa estado() {
        return disponible ? new EstadoIa(true, List.of("qwen3:4b", "bge-m3"), "fake listo") : EstadoIa.noDisponible("fake apagado");
    }

    @Override
    public RespuestaChat chat(PeticionChat peticion, Consumer<String> alRecibirToken) {
        exigirDisponible();
        chatsRecibidos.add(peticion);
        int intentos = 0;
        String ultimo = "";
        while (intentos <= peticion.reintentosMaximos()) {
            intentos++;
            String texto = respuestasChat.isEmpty() ? "¿Qué te hace pensar eso?" : respuestasChat.poll();
            for (String token : texto.split("(?<= )")) {
                alRecibirToken.accept(token);
            }
            ultimo = texto.trim();
            if (peticion.validador().isEmpty() || peticion.validador().get().test(ultimo)) {
                return new RespuestaChat(ultimo, "qwen3:4b", "sha256:fake", intentos);
            }
        }
        throw new IaRespuestaInvalida("La respuesta no pasó el validador tras " + intentos + " intentos");
    }

    @Override
    public Clasificacion clasificar(PeticionClasificacion peticion) {
        exigirDisponible();
        clasificacionesRecibidas.add(peticion);
        String crudo = respuestasCrudasClasificacion.isEmpty()
                ? "{\"etiqueta\":\"" + peticion.etiquetas().getFirst() + "\",\"por_que\":\"fake\"}"
                : respuestasCrudasClasificacion.poll();
        String etiqueta = extraer(crudo, "etiqueta");
        String porQue = extraer(crudo, "por_que");
        if (etiqueta == null) {
            throw new IaRespuestaInvalida("El modelo no devolvió JSON válido: " + crudo);
        }
        if (!peticion.etiquetas().contains(etiqueta)) {
            throw new IaRespuestaInvalida("Etiqueta fuera del enum: " + etiqueta);
        }
        return new Clasificacion(etiqueta, porQue == null ? "" : porQue, "qwen3:4b", "sha256:fake");
    }

    @Override
    public List<float[]> incrustar(PeticionEmbeddings peticion) {
        exigirDisponible();
        List<float[]> vectores = new ArrayList<>();
        for (String texto : peticion.textos()) {
            float[] v = new float[dimension];
            int h = texto.hashCode();
            for (int i = 0; i < dimension; i++) {
                v[i] = ((h >> (i % 31)) & 1) == 0 ? 0.01f : -0.01f;
            }
            vectores.add(v);
        }
        return vectores;
    }

    private void exigirDisponible() {
        if (!disponible) {
            throw new IaNoDisponible("fake apagado");
        }
        if (lento) {
            throw new IaTiempoAgotado("fake lento");
        }
    }

    /** Extracción mínima de un campo string de un objeto JSON plano; null si el texto no es un objeto JSON con ese campo. */
    private static String extraer(String json, String campo) {
        if (json == null) {
            return null;
        }
        String recortado = json.trim();
        if (!recortado.startsWith("{") || !recortado.endsWith("}")) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + campo + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(recortado);
        return m.find() ? m.group(1) : null;
    }
}
