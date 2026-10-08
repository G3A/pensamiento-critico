package pensamiento.argdown;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import pensamiento.nucleo.argdown.DocumentoArgdown;
import pensamiento.nucleo.argdown.DocumentoArgdown.ArgumentoArgdown;
import pensamiento.nucleo.argdown.DocumentoArgdown.Elemento;
import pensamiento.nucleo.argdown.DocumentoArgdown.Enunciado;
import pensamiento.nucleo.argdown.DocumentoArgdown.Marca;
import pensamiento.nucleo.argdown.DocumentoArgdown.Relacion;
import pensamiento.nucleo.argdown.ErrorSintaxisArgdown;
import pensamiento.nucleo.puertos.Argdown;

/**
 * Parser y escritor del subconjunto Argdown de docs/argdown-subconjunto.md: enunciados con título opcional,
 * argumentos con nombre, apoyo (+) y ataque (-) por indentación de dos espacios, las marcas #oculta y #asumible
 * y el peso {peso: N}. Cada error dice línea, columna y cómo corregirlo, en español.
 */
@Component
public class ParserArgdown implements Argdown {

    public static final int MAXIMO_CARACTERES = 8000;
    public static final int MAXIMO_LINEAS = 200;
    public static final int MAXIMO_NIVEL = 8;
    public static final int MAXIMO_ENUNCIADOS = 60;
    public static final int LARGO_TITULO = 80;
    public static final int LARGO_TEXTO = 300;

    private static final Pattern PESO = Pattern.compile("\\s*\\{peso:\\s*(\\d{1,3})}$");
    private static final Pattern MARCA = Pattern.compile("\\s+#(oculta|asumible)$");

    // ------------------------------------------------------------------------------------------------
    // Leer
    // ------------------------------------------------------------------------------------------------

    /** Nodo mutable mientras se arma el árbol; al final se congela en los records del núcleo. */
    private static final class Nodo {
        final int linea;
        final int columna;
        final boolean argumento;
        final String titulo;
        final String texto;
        final Marca marca;
        final List<Hijo> hijos = new ArrayList<>();

        Nodo(int linea, int columna, boolean argumento, String titulo, String texto, Marca marca) {
            this.linea = linea;
            this.columna = columna;
            this.argumento = argumento;
            this.titulo = titulo;
            this.texto = texto;
            this.marca = marca;
        }
    }

    private record Hijo(Relacion.Tipo tipo, Integer peso, Nodo nodo) {
    }

    private record Leido(Nodo nodo, Integer peso, int columnaPeso) {
    }

    @Override
    public DocumentoArgdown leer(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new ErrorSintaxisArgdown(1, 1, "El texto está vacío: escribe al menos una conclusión.");
        }
        if (texto.length() > MAXIMO_CARACTERES) {
            throw new ErrorSintaxisArgdown(1, 1, "El texto pasa de " + MAXIMO_CARACTERES + " caracteres: divide el mapa en dos.");
        }
        String[] lineas = texto.split("\r?\n", -1);
        if (lineas.length > MAXIMO_LINEAS) {
            throw new ErrorSintaxisArgdown(MAXIMO_LINEAS + 1, 1, "El mapa admite como máximo " + MAXIMO_LINEAS + " líneas.");
        }
        List<Nodo> raices = new ArrayList<>();
        List<Nodo> pila = new ArrayList<>();
        List<Nodo> todos = new ArrayList<>();
        for (int i = 0; i < lineas.length; i++) {
            int numero = i + 1;
            String linea = lineas[i].stripTrailing();
            if (linea.isBlank()) {
                continue;
            }
            int espacios = 0;
            while (espacios < linea.length() && (linea.charAt(espacios) == ' ' || linea.charAt(espacios) == '\t')) {
                if (linea.charAt(espacios) == '\t') {
                    throw new ErrorSintaxisArgdown(numero, espacios + 1, "Usa espacios para indentar, no tabulaciones.");
                }
                espacios++;
            }
            if (espacios % 2 != 0) {
                throw new ErrorSintaxisArgdown(numero, espacios + 1, "La indentación va de dos en dos espacios.");
            }
            int nivel = espacios / 2;
            String contenido = linea.substring(espacios);
            int columna = espacios + 1;
            if (nivel == 0) {
                Nodo raiz = raiz(contenido, numero, columna);
                raices.add(raiz);
                todos.add(raiz);
                pila.clear();
                pila.add(raiz);
                continue;
            }
            if (nivel > MAXIMO_NIVEL) {
                throw new ErrorSintaxisArgdown(numero, columna, "El mapa admite como máximo " + MAXIMO_NIVEL + " niveles de indentación.");
            }
            if (pila.size() < nivel) {
                throw new ErrorSintaxisArgdown(numero, columna,
                        "La indentación salta más de un nivel: esta línea necesita arriba un enunciado con un nivel menos.");
            }
            Nodo padre = pila.get(nivel - 1);
            Nodo nodo = relacion(contenido, numero, columna, padre);
            todos.add(nodo);
            while (pila.size() > nivel) {
                pila.removeLast();
            }
            pila.add(nodo);
        }
        if (raices.isEmpty()) {
            throw new ErrorSintaxisArgdown(1, 1, "El texto está vacío: escribe al menos una conclusión.");
        }
        revisar(todos);
        return new DocumentoArgdown(raices.stream().map(n -> (Enunciado) congelar(n)).toList());
    }

    private static Nodo raiz(String contenido, int numero, int columna) {
        if (contenido.startsWith("+") || contenido.startsWith("-")) {
            throw new ErrorSintaxisArgdown(numero, columna,
                    "Una relación (+ o -) necesita arriba un enunciado con un nivel menos de indentación.");
        }
        Leido leido = elemento(contenido, numero, columna);
        if (leido.nodo().argumento) {
            throw new ErrorSintaxisArgdown(numero, columna, "Un argumento <…> va después de un + o un -, debajo de lo que apoya o ataca.");
        }
        if (leido.peso() != null) {
            throw new ErrorSintaxisArgdown(numero, leido.columnaPeso(), "El peso va en una relación (+ o -), no en una conclusión.");
        }
        return leido.nodo();
    }

    /** Una línea indentada: + o -, un espacio y el elemento, que queda colgado de su padre. */
    private static Nodo relacion(String contenido, int numero, int columna, Nodo padre) {
        Relacion.Tipo tipo;
        if (contenido.startsWith("+")) {
            tipo = Relacion.Tipo.APOYO;
        } else if (contenido.startsWith("-")) {
            tipo = Relacion.Tipo.ATAQUE;
        } else {
            throw new ErrorSintaxisArgdown(numero, columna, "Se esperaba + (apoyo) o - (ataque) al inicio de la línea indentada.");
        }
        if (contenido.length() < 2 || contenido.charAt(1) != ' ') {
            throw new ErrorSintaxisArgdown(numero, columna + 1, "Deja un espacio después del " + contenido.charAt(0) + ".");
        }
        int salto = 1;
        while (salto < contenido.length() && contenido.charAt(salto) == ' ') {
            salto++;
        }
        if (salto == contenido.length()) {
            throw new ErrorSintaxisArgdown(numero, columna + salto, "Falta el enunciado después del " + contenido.charAt(0) + ".");
        }
        Leido leido = elemento(contenido.substring(salto), numero, columna + salto);
        if (padre.argumento) {
            if (tipo != Relacion.Tipo.APOYO) {
                throw new ErrorSintaxisArgdown(numero, columna,
                        "Un argumento solo lleva premisas con +; para atacarlo, ataca lo que concluye o una de sus premisas.");
            }
            if (leido.nodo().argumento) {
                throw new ErrorSintaxisArgdown(numero, columna + salto, "Las premisas de un argumento son enunciados, no otros argumentos.");
            }
            if (leido.peso() != null) {
                throw new ErrorSintaxisArgdown(numero, leido.columnaPeso(),
                        "El peso va en la línea del argumento <" + padre.titulo + ">, no en sus premisas.");
            }
        }
        padre.hijos.add(new Hijo(tipo, leido.peso(), leido.nodo()));
        return leido.nodo();
    }

    /** Definiciones únicas, referencias definidas, argumentos con premisas y tope de enunciados. */
    private static void revisar(List<Nodo> todos) {
        Map<String, Nodo> enunciados = new HashMap<>();
        Map<String, Nodo> argumentos = new HashMap<>();
        int contados = 0;
        for (Nodo n : todos) {
            if (n.argumento) {
                Nodo previo = argumentos.putIfAbsent(n.titulo, n);
                if (previo != null) {
                    throw new ErrorSintaxisArgdown(n.linea, n.columna,
                            "Ya hay un argumento <" + n.titulo + "> en la línea " + previo.linea + ": usa otro nombre.");
                }
                if (n.hijos.isEmpty()) {
                    throw new ErrorSintaxisArgdown(n.linea, n.columna,
                            "El argumento <" + n.titulo + "> no tiene premisas: agrega debajo líneas + con sus premisas.");
                }
                continue;
            }
            if (n.texto == null) {
                continue;
            }
            contados++;
            if (contados > MAXIMO_ENUNCIADOS) {
                throw new ErrorSintaxisArgdown(n.linea, n.columna, "El mapa admite como máximo " + MAXIMO_ENUNCIADOS + " enunciados.");
            }
            if (n.titulo != null) {
                Nodo previo = enunciados.putIfAbsent(n.titulo, n);
                if (previo != null) {
                    throw new ErrorSintaxisArgdown(n.linea, n.columna, "El enunciado [" + n.titulo + "] ya está definido en la línea "
                            + previo.linea + "; para volver a usarlo escribe solo [" + n.titulo + "].");
                }
            }
        }
        for (Nodo n : todos) {
            if (!n.argumento && n.texto == null && !enunciados.containsKey(n.titulo)) {
                throw new ErrorSintaxisArgdown(n.linea, n.columna,
                        "El enunciado [" + n.titulo + "] no está definido: escribe [" + n.titulo + "]: y su texto en alguna línea.");
            }
        }
    }

    private static Elemento congelar(Nodo n) {
        List<Relacion> relaciones = n.hijos.stream().map(h -> new Relacion(h.tipo(), h.peso(), congelar(h.nodo()))).toList();
        return n.argumento ? new ArgumentoArgdown(n.titulo, n.texto, relaciones) : new Enunciado(n.titulo, n.texto, n.marca, relaciones);
    }

    /** Lee lo que va después del + o el - (o la línea entera de una conclusión), con su peso y su marca. */
    private static Leido elemento(String s, int linea, int columna) {
        Integer peso = null;
        int columnaPeso = 0;
        Matcher mp = PESO.matcher(s);
        if (mp.find()) {
            columnaPeso = columna + mp.start() + (mp.group().length() - mp.group().stripLeading().length());
            peso = Integer.parseInt(mp.group(1));
            if (peso > DocumentoArgdown.MAXIMO_PESO) {
                throw new ErrorSintaxisArgdown(linea, columnaPeso, "El peso va de 0 a " + DocumentoArgdown.MAXIMO_PESO + ".");
            }
            s = s.substring(0, mp.start());
        }
        Marca marca = null;
        int columnaMarca = 0;
        Matcher mm = MARCA.matcher(s);
        if (mm.find()) {
            marca = Marca.valueOf(mm.group(1).toUpperCase(Locale.ROOT));
            columnaMarca = columna + mm.start() + (mm.group().length() - mm.group().stripLeading().length());
            s = s.substring(0, mm.start());
            if (MARCA.matcher(s).find()) {
                throw new ErrorSintaxisArgdown(linea, columnaMarca, "Un enunciado lleva una sola marca: #oculta o #asumible.");
            }
        }
        if (s.startsWith("<")) {
            int cierre = s.indexOf('>');
            if (cierre < 0) {
                throw new ErrorSintaxisArgdown(linea, columna, "Falta cerrar el nombre del argumento con >.");
            }
            String titulo = titulo(s.substring(1, cierre), linea, columna + 1);
            String texto = textoDespuesDelTitulo(s.substring(cierre + 1), linea, columna + cierre + 1, "<" + titulo + ">");
            if (marca != null) {
                throw new ErrorSintaxisArgdown(linea, columnaMarca, "Las marcas #oculta y #asumible van en enunciados, no en argumentos.");
            }
            return new Leido(new Nodo(linea, columna, true, titulo, texto, null), peso, columnaPeso);
        }
        if (s.startsWith("[")) {
            int cierre = s.indexOf(']');
            if (cierre < 0) {
                throw new ErrorSintaxisArgdown(linea, columna, "Falta cerrar el título del enunciado con ].");
            }
            String titulo = titulo(s.substring(1, cierre), linea, columna + 1);
            String texto = textoDespuesDelTitulo(s.substring(cierre + 1), linea, columna + cierre + 1, "[" + titulo + "]");
            if (texto == null && marca != null) {
                throw new ErrorSintaxisArgdown(linea, columnaMarca,
                        "La marca va en la línea donde se define [" + titulo + "], junto a su texto.");
            }
            return new Leido(new Nodo(linea, columna, false, titulo, texto, marca), peso, columnaPeso);
        }
        String texto = s.strip();
        if (texto.isEmpty()) {
            throw new ErrorSintaxisArgdown(linea, columna, "Falta el texto del enunciado.");
        }
        if (texto.startsWith("+") || texto.startsWith("-")) {
            throw new ErrorSintaxisArgdown(linea, columna,
                    "Un enunciado sin título no puede empezar con + ni -: ponle título, por ejemplo [Título]: " + texto);
        }
        largo(texto, linea, columna);
        return new Leido(new Nodo(linea, columna, false, null, texto, marca), peso, columnaPeso);
    }

    private static String titulo(String crudo, int linea, int columna) {
        String titulo = crudo.strip();
        if (titulo.isEmpty()) {
            throw new ErrorSintaxisArgdown(linea, columna, "El título está vacío.");
        }
        if (titulo.length() > LARGO_TITULO) {
            throw new ErrorSintaxisArgdown(linea, columna, "El título pasa de " + LARGO_TITULO + " caracteres.");
        }
        for (int i = 0; i < crudo.length(); i++) {
            if ("[]<>".indexOf(crudo.charAt(i)) >= 0) {
                throw new ErrorSintaxisArgdown(linea, columna + i, "Un título no puede llevar [, ], < ni >.");
            }
        }
        return titulo;
    }

    /** Lo que sigue al título: nada (referencia) o dos puntos y el texto. */
    private static String textoDespuesDelTitulo(String resto, int linea, int columna, String cabeza) {
        if (resto.isBlank()) {
            return null;
        }
        if (!resto.startsWith(":")) {
            throw new ErrorSintaxisArgdown(linea, columna, "Después de " + cabeza + " van dos puntos y el texto, o nada.");
        }
        String texto = resto.substring(1).strip();
        if (texto.isEmpty()) {
            throw new ErrorSintaxisArgdown(linea, columna + 1, "Falta el texto después de los dos puntos.");
        }
        largo(texto, linea, columna + 2);
        return texto;
    }

    private static void largo(String texto, int linea, int columna) {
        if (texto.length() > LARGO_TEXTO) {
            throw new ErrorSintaxisArgdown(linea, columna, "El enunciado pasa de " + LARGO_TEXTO + " caracteres: divídelo en dos.");
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Escribir
    // ------------------------------------------------------------------------------------------------

    @Override
    public String escribir(DocumentoArgdown documento) {
        if (documento.raices().isEmpty()) {
            throw new IllegalArgumentException("Un documento Argdown necesita al menos una conclusión");
        }
        List<String> bloques = new ArrayList<>();
        for (Enunciado raiz : documento.raices()) {
            List<String> lineas = new ArrayList<>();
            escribir(raiz, null, 0, lineas);
            bloques.add(String.join("\n", lineas));
        }
        return String.join("\n\n", bloques);
    }

    private static void escribir(Elemento elemento, Relacion relacion, int nivel, List<String> lineas) {
        StringBuilder sb = new StringBuilder("  ".repeat(nivel));
        if (relacion != null) {
            sb.append(relacion.tipo() == Relacion.Tipo.APOYO ? "+ " : "- ");
        }
        List<Relacion> hijos;
        String cuerpo;
        Marca marca = null;
        if (elemento instanceof Enunciado e) {
            cuerpo = e.titulo() == null ? representable(e.texto(), true) : "[" + tituloEscribible(e.titulo()) + "]" + dosPuntos(e.texto());
            marca = e.marca();
            hijos = e.relaciones();
        } else {
            ArgumentoArgdown a = (ArgumentoArgdown) elemento;
            cuerpo = "<" + tituloEscribible(a.titulo()) + ">" + dosPuntos(a.texto());
            hijos = a.premisas();
        }
        if (PESO.matcher(cuerpo).find() || MARCA.matcher(cuerpo).find()) {
            throw new IllegalArgumentException("El texto termina como una marca o un peso y no se podría volver a leer: " + cuerpo);
        }
        sb.append(cuerpo);
        if (marca != null) {
            sb.append(' ').append(marca.etiqueta());
        }
        if (relacion != null && relacion.peso() != null) {
            sb.append(" {peso: ").append(relacion.peso()).append('}');
        }
        lineas.add(sb.toString());
        for (Relacion r : hijos) {
            escribir(r.destino(), r, nivel + 1, lineas);
        }
    }

    private static String dosPuntos(String texto) {
        return texto == null ? "" : ": " + representable(texto, false);
    }

    private static String tituloEscribible(String titulo) {
        if (titulo.isBlank() || !titulo.equals(titulo.strip()) || titulo.length() > LARGO_TITULO
                || titulo.chars().anyMatch(c -> "[]<>\n\r".indexOf(c) >= 0)) {
            throw new IllegalArgumentException("Título que no cabe en el subconjunto: " + titulo);
        }
        return titulo;
    }

    private static String representable(String texto, boolean sinTitulo) {
        if (texto.isBlank() || !texto.equals(texto.strip()) || texto.length() > LARGO_TEXTO || texto.contains("\n") || texto.contains("\r")) {
            throw new IllegalArgumentException("Texto que no cabe en el subconjunto: " + texto);
        }
        if (sinTitulo && "[<+-".indexOf(texto.charAt(0)) >= 0) {
            throw new IllegalArgumentException("Un texto sin título no puede empezar con [, <, + ni -: " + texto);
        }
        return texto;
    }
}
