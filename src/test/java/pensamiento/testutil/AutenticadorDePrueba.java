package pensamiento.testutil;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Un autenticador de WebAuthn en software, solo para pruebas (hito 9): genera una clave ES256 (P-256), responde al registro
 * con una atestación "none" y firma las confirmaciones como lo haría la llave de acceso de un celular o un portátil. Recibe
 * las opciones tal como las manda el servidor (JSON de Spring Security) y devuelve el JSON que mandaría /llave.js. El CBOR
 * de la atestación y de la clave COSE se escribe a mano: son tres mapas pequeños.
 */
public final class AutenticadorDePrueba {

    public static final String RP_ID = "localhost";
    public static final String ORIGEN = "http://localhost:8080";

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DE_B64 = Base64.getUrlDecoder();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final KeyPair par;
    private final byte[] credencial = new byte[16];
    private final String origen;
    private final String rpId;
    private byte[] entidadUsuario = new byte[0];
    private int contador;

    public AutenticadorDePrueba() {
        this(RP_ID, ORIGEN);
    }

    public AutenticadorDePrueba(String rpId, String origen) {
        this.rpId = rpId;
        this.origen = origen;
        try {
            KeyPairGenerator generador = KeyPairGenerator.getInstance("EC");
            generador.initialize(new ECGenParameterSpec("secp256r1"));
            this.par = generador.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
        new SecureRandom().nextBytes(credencial);
    }

    /** El JSON del registro ({credential, label}) para las opciones de creación que mandó el servidor. */
    public String registrar(String opcionesJson) {
        JsonNode opciones = JSON.readTree(opcionesJson);
        entidadUsuario = DE_B64.decode(opciones.get("user").get("id").asString());
        byte[] cliente = datosDelCliente("webauthn.create", opciones.get("challenge").asString());
        ByteArrayOutputStream datos = new ByteArrayOutputStream();
        datos.writeBytes(sha256(rpId.getBytes(StandardCharsets.UTF_8)));
        datos.write(0x45);                                  // UP | UV | AT
        datos.writeBytes(ByteBuffer.allocate(4).putInt(contador).array());
        datos.writeBytes(new byte[16]);                     // AAGUID en ceros
        datos.writeBytes(ByteBuffer.allocate(2).putShort((short) credencial.length).array());
        datos.writeBytes(credencial);
        datos.writeBytes(claveCose());
        Cbor atestacion = new Cbor().mapa(3).texto("fmt").texto("none").texto("attStmt").mapa(0).texto("authData").bytes(datos.toByteArray());

        ObjectNode cuerpo = JSON.createObjectNode();
        ObjectNode credencialJson = cuerpo.putObject("credential");
        credencialJson.put("id", B64.encodeToString(credencial)).put("rawId", B64.encodeToString(credencial)).put("type", "public-key")
                .put("authenticatorAttachment", "platform");
        credencialJson.putObject("clientExtensionResults");
        ObjectNode respuesta = credencialJson.putObject("response");
        respuesta.put("attestationObject", B64.encodeToString(atestacion.aBytes())).put("clientDataJSON", B64.encodeToString(cliente));
        respuesta.putArray("transports").add("internal");
        cuerpo.put("label", "Autenticador de prueba");
        return cuerpo.toString();
    }

    /** El JSON de una confirmación firmada para las opciones de petición que mandó el servidor. */
    public String firmar(String opcionesJson) {
        JsonNode opciones = JSON.readTree(opcionesJson);
        byte[] cliente = datosDelCliente("webauthn.get", opciones.get("challenge").asString());
        contador++;
        ByteArrayOutputStream datos = new ByteArrayOutputStream();
        datos.writeBytes(sha256(rpId.getBytes(StandardCharsets.UTF_8)));
        datos.write(0x05);                                  // UP | UV
        datos.writeBytes(ByteBuffer.allocate(4).putInt(contador).array());
        byte[] datosDelAutenticador = datos.toByteArray();
        byte[] firma;
        try {
            Signature ecdsa = Signature.getInstance("SHA256withECDSA");
            ecdsa.initSign(par.getPrivate());
            ecdsa.update(datosDelAutenticador);
            ecdsa.update(sha256(cliente));
            firma = ecdsa.sign();                           // DER, como pide WebAuthn para ES256
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
        ObjectNode cuerpo = JSON.createObjectNode();
        cuerpo.put("id", B64.encodeToString(credencial)).put("rawId", B64.encodeToString(credencial)).put("type", "public-key")
                .put("authenticatorAttachment", "platform");
        cuerpo.putObject("clientExtensionResults");
        ObjectNode respuesta = cuerpo.putObject("response");
        respuesta.put("authenticatorData", B64.encodeToString(datosDelAutenticador)).put("clientDataJSON", B64.encodeToString(cliente))
                .put("signature", B64.encodeToString(firma)).put("userHandle", B64.encodeToString(entidadUsuario));
        return cuerpo.toString();
    }

    private byte[] datosDelCliente(String tipo, String reto) {
        ObjectNode cliente = JSON.createObjectNode();
        cliente.put("type", tipo).put("challenge", reto).put("origin", origen).put("crossOrigin", false);
        return cliente.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] claveCose() {
        ECPublicKey publica = (ECPublicKey) par.getPublic();
        return new Cbor().mapa(5)
                .entero(1).entero(2)                        // kty: EC2
                .entero(3).entero(-7)                       // alg: ES256
                .entero(-1).entero(1)                       // crv: P-256
                .entero(-2).bytes(coordenada(publica.getW().getAffineX()))
                .entero(-3).bytes(coordenada(publica.getW().getAffineY()))
                .aBytes();
    }

    private static byte[] coordenada(BigInteger valor) {
        byte[] crudo = valor.toByteArray();
        byte[] fija = new byte[32];
        int largo = Math.min(crudo.length, 32);
        System.arraycopy(crudo, crudo.length - largo, fija, 32 - largo, largo);
        return fija;
    }

    private static byte[] sha256(byte[] datos) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(datos);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** CBOR mínimo: enteros, bytes, texto y mapas, lo que usan la atestación "none" y una clave COSE EC2. */
    private static final class Cbor {
        private final ByteArrayOutputStream salida = new ByteArrayOutputStream();

        Cbor entero(long n) {
            return n >= 0 ? cabecera(0, n) : cabecera(1, -1 - n);
        }

        Cbor bytes(byte[] b) {
            cabecera(2, b.length);
            salida.writeBytes(b);
            return this;
        }

        Cbor texto(String t) {
            byte[] b = t.getBytes(StandardCharsets.UTF_8);
            cabecera(3, b.length);
            salida.writeBytes(b);
            return this;
        }

        Cbor mapa(int pares) {
            return cabecera(5, pares);
        }

        byte[] aBytes() {
            return salida.toByteArray();
        }

        private Cbor cabecera(int mayor, long n) {
            if (n < 24) {
                salida.write((mayor << 5) | (int) n);
            } else if (n < 256) {
                salida.write((mayor << 5) | 24);
                salida.write((int) n);
            } else {
                salida.write((mayor << 5) | 25);
                salida.write((int) (n >> 8));
                salida.write((int) n & 0xff);
            }
            return this;
        }
    }
}
