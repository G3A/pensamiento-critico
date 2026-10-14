package pensamiento.web.aula;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import pensamiento.nucleo.Usuario;

/**
 * Lee la lista de un grupo para el alta en lote (hito 10, entregable 3): una persona por línea, pegada o en un CSV (la primera
 * columna, separada por coma o punto y coma, con o sin comillas; una primera fila "nombre" se toma como encabezado). Las
 * líneas vacías no cuentan. Marca las filas inválidas y las repetidas dentro de la misma lista (sin distinguir mayúsculas,
 * tildes ni espacios); si hay más de {@link #MAXIMO} filas, la lista entera se rechaza. Sin base de datos: quién ya existe en
 * la institución lo decide el servicio.
 */
public final class ListaDeEstudiantes {

    public static final int MAXIMO = 500;

    public enum Estado {
        NUEVA("se crea"), REPETIDA_EN_LA_LISTA("repetida en la lista"), YA_EXISTE("ya hay una cuenta con ese nombre"), INVALIDA("nombre inválido");

        private final String texto;

        Estado(String texto) {
            this.texto = texto;
        }

        public String texto() {
            return texto;
        }
    }

    /** Una fila de la lista: su número de línea (desde 1), el nombre tal como quedará y qué pasa con ella. */
    public record Fila(int linea, String nombre, Estado estado) {

        public Fila conEstado(Estado nuevo) {
            return new Fila(linea, nombre, nuevo);
        }
    }

    public static class Demasiadas extends RuntimeException {
        public Demasiadas(int filas) {
            super("La lista tiene " + filas + " filas: el máximo es " + MAXIMO + ". Divídela en partes.");
        }
    }

    private ListaDeEstudiantes() {
    }

    public static List<Fila> leer(String texto) {
        List<Fila> filas = new ArrayList<>();
        Set<String> vistas = new HashSet<>();
        String[] lineas = (texto == null ? "" : texto.replace("﻿", "")).split("\r\n|\r|\n", -1);
        int contadas = 0;
        for (int i = 0; i < lineas.length; i++) {
            String nombre = primeraColumna(lineas[i]).replaceAll("\\s+", " ").strip();
            if (nombre.isEmpty() || contadas == 0 && "nombre".equals(nombre.toLowerCase(Locale.ROOT))) {
                continue;
            }
            contadas++;
            Estado estado;
            if (nombre.length() > 60) {
                estado = Estado.INVALIDA;
            } else {
                estado = vistas.add(Usuario.claveDeNombre(nombre)) ? Estado.NUEVA : Estado.REPETIDA_EN_LA_LISTA;
            }
            filas.add(new Fila(i + 1, nombre, estado));
        }
        if (filas.size() > MAXIMO) {
            throw new Demasiadas(filas.size());
        }
        return filas;
    }

    /** La primera columna de una línea de CSV: hasta la primera coma o punto y coma fuera de comillas, sin las comillas. */
    static String primeraColumna(String linea) {
        StringBuilder campo = new StringBuilder();
        boolean entreComillas = false;
        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            if (c == '"') {
                if (entreComillas && i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
                    campo.append('"');
                    i++;
                } else {
                    entreComillas = !entreComillas;
                }
            } else if ((c == ',' || c == ';' || c == '\t') && !entreComillas) {
                break;
            } else {
                campo.append(c);
            }
        }
        return campo.toString();
    }
}
