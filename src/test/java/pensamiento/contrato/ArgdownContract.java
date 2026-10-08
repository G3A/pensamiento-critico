package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import pensamiento.nucleo.argdown.DocumentoArgdown;
import pensamiento.nucleo.argdown.DocumentoArgdown.ArgumentoArgdown;
import pensamiento.nucleo.argdown.DocumentoArgdown.Elemento;
import pensamiento.nucleo.argdown.DocumentoArgdown.Enunciado;
import pensamiento.nucleo.argdown.DocumentoArgdown.Marca;
import pensamiento.nucleo.argdown.DocumentoArgdown.Relacion;
import pensamiento.nucleo.argdown.ErrorSintaxisArgdown;
import pensamiento.nucleo.puertos.Argdown;

/**
 * Contrato del puerto Argdown (RF-09, docs/argdown-subconjunto.md): leer y escribir son inversas sobre el
 * subconjunto (propiedad de ida y vuelta), los errores dicen línea y columna en español, y el texto conserva
 * tildes y eñes. El adaptador real es puro y en memoria, así que su subclase corre en cada PR y no hay Fake.
 */
public abstract class ArgdownContract {

    protected abstract Argdown crearSut();

    private static final String SUCURSAL = """
            [Sucursal]: Conviene abrir la segunda sucursal en el centro.
              + <Más ventas>: Con más gente pasando, se vende más. {peso: 3}
                + [Tráfico]: El centro tiene más tráfico peatonal que el barrio. #asumible
                + [Conversión]: Más tráfico da más ventas. #asumible
              - [Personal]: Falta personal para atender dos locales.""";

    // ------------------------------------------------------------------------------------------------
    // Propiedad de ida y vuelta
    // ------------------------------------------------------------------------------------------------

    @Property(tries = 300)
    void escribir_y_volver_a_leer_devuelve_el_mismo_arbol(@ForAll long semilla) {
        Argdown argdown = crearSut();
        DocumentoArgdown documento = new Generador(new Random(semilla)).documento();

        String texto = argdown.escribir(documento);

        assertThat(argdown.leer(texto)).isEqualTo(documento);
    }

    @Property(tries = 300)
    void leer_y_volver_a_escribir_un_texto_canonico_devuelve_el_mismo_texto(@ForAll long semilla) {
        Argdown argdown = crearSut();
        String canonico = argdown.escribir(new Generador(new Random(semilla)).documento());

        assertThat(argdown.escribir(argdown.leer(canonico))).isEqualTo(canonico);
    }

    // ------------------------------------------------------------------------------------------------
    // Estructura
    // ------------------------------------------------------------------------------------------------

    @Test
    void un_argumento_con_nombre_reune_sus_premisas_con_marca_y_el_peso_va_en_su_relacion() {
        DocumentoArgdown d = crearSut().leer(SUCURSAL);

        assertThat(d.raices()).hasSize(1);
        Enunciado sucursal = d.raices().getFirst();
        assertThat(sucursal.titulo()).isEqualTo("Sucursal");
        assertThat(sucursal.texto()).isEqualTo("Conviene abrir la segunda sucursal en el centro.");
        assertThat(sucursal.relaciones()).extracting(Relacion::tipo).containsExactly(Relacion.Tipo.APOYO, Relacion.Tipo.ATAQUE);
        Relacion apoyo = sucursal.relaciones().getFirst();
        assertThat(apoyo.peso()).isEqualTo(3);
        ArgumentoArgdown masVentas = (ArgumentoArgdown) apoyo.destino();
        assertThat(masVentas.titulo()).isEqualTo("Más ventas");
        assertThat(masVentas.texto()).isEqualTo("Con más gente pasando, se vende más.");
        assertThat(masVentas.premisas()).extracting(r -> ((Enunciado) r.destino()).titulo()).containsExactly("Tráfico", "Conversión");
        assertThat(masVentas.premisas()).extracting(r -> ((Enunciado) r.destino()).marca()).containsOnly(Marca.ASUMIBLE);
        Relacion ataque = sucursal.relaciones().get(1);
        assertThat(ataque.peso()).isNull();
        assertThat(ataque.pesoEfectivo()).isEqualTo(1);
        assertThat(((Enunciado) ataque.destino()).texto()).isEqualTo("Falta personal para atender dos locales.");
    }

    @Test
    void el_texto_conserva_tildes_enies_y_signos() {
        String texto = "¿Añadimos más pan de bono? Sí: «el año pasado» se vendió 30% más; ñandú {x} #etiqueta [nota]";
        DocumentoArgdown d = crearSut().leer(texto);
        assertThat(d.raices().getFirst().texto()).isEqualTo(texto);
        assertThat(d.raices().getFirst().titulo()).isNull();
    }

    @Test
    void una_referencia_sin_texto_apunta_a_un_enunciado_definido_antes_o_despues() {
        String texto = """
                [Carro]: Nos conviene comprar un carro usado.
                  + [Ahorro]

                [Ahorro]: Tenemos ahorrado el 60% del precio.""";
        DocumentoArgdown d = crearSut().leer(texto);
        assertThat(d.raices()).hasSize(2);
        Enunciado referencia = (Enunciado) d.raices().getFirst().relaciones().getFirst().destino();
        assertThat(referencia.esReferencia()).isTrue();
        assertThat(referencia.titulo()).isEqualTo("Ahorro");
    }

    @Test
    void escribir_normaliza_espacios_y_separa_las_conclusiones_con_una_linea_en_blanco() {
        String desordenado = "[A]:Primera conclusión.   \r\n  +   [B]:   Un apoyo.  {peso:2}\r\n\r\n\r\nSegunda conclusión.\r\n  - Un ataque. #oculta\r\n";
        String canonico = """
                [A]: Primera conclusión.
                  + [B]: Un apoyo. {peso: 2}

                Segunda conclusión.
                  - Un ataque. #oculta""";
        assertThat(crearSut().escribir(crearSut().leer(desordenado))).isEqualTo(canonico);
    }

    // ------------------------------------------------------------------------------------------------
    // Errores con línea y columna
    // ------------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            relación sin conclusión arriba          | '  + Sin conclusión arriba'                                     | 1 | 3 | La indentación salta más de un nivel
            indentación impar                       | 'Conclusión.\n   + Tres espacios'                               | 2 | 4 | La indentación va de dos en dos espacios.
            tabulación                              | 'Conclusión.\n\t+ Con tabulación'                               | 2 | 1 | Usa espacios para indentar, no tabulaciones.
            viñeta en vez de signo                  | 'Conclusión.\n  * Viñeta'                                       | 2 | 3 | Se esperaba + (apoyo) o - (ataque)
            signo pegado                            | 'Conclusión.\n  +Pegado'                                        | 2 | 4 | Deja un espacio después del +.
            salto de dos niveles                    | 'Conclusión.\n    + Muy adentro'                                | 2 | 5 | La indentación salta más de un nivel
            título sin cerrar                       | 'Conclusión.\n  + [Tráfico'                                     | 2 | 5 | Falta cerrar el título del enunciado con ].
            referencia sin definir                  | 'Conclusión.\n  + [Otro]'                                       | 2 | 5 | El enunciado [Otro] no está definido
            definición repetida                     | '[A]: Uno.\n  + [A]: Dos.'                                      | 2 | 5 | El enunciado [A] ya está definido en la línea 1
            ataque bajo un argumento                | 'Conclusión.\n  - <Ventas>\n    - Un ataque.'                   | 3 | 5 | Un argumento solo lleva premisas con +
            argumento sin premisas                  | 'Conclusión.\n  + <Ventas>'                                     | 2 | 5 | El argumento <Ventas> no tiene premisas
            argumento como conclusión               | '<Ventas>: Se vende más.'                                       | 1 | 1 | Un argumento <…> va después de un + o un -
            peso en una premisa de argumento        | 'Conclusión.\n  + <Ventas>\n    + Premisa. {peso: 2}'           | 3 | 16 | El peso va en la línea del argumento <Ventas>
            peso fuera de rango                     | 'Conclusión.\n  + Apoyo. {peso: 12}'                            | 2 | 12 | El peso va de 0 a 9.
            marca en una referencia                 | '[A]: Uno.\n  + [B] #oculta\n\n[B]: Dos.'                       | 2 | 9 | La marca va en la línea donde se define [B]
            dos marcas                              | 'Conclusión.\n  + Apoyo. #oculta #asumible'                     | 2 | 20 | Un enunciado lleva una sola marca
            texto sin título que empieza con signo  | 'Conclusión.\n  + - menos tres grados'                          | 2 | 5 | Un enunciado sin título no puede empezar con + ni -
            """)
    void un_texto_fuera_del_subconjunto_dice_linea_columna_y_como_corregirlo(String caso, String texto, int linea, int columna, String explicacion) {
        String real = texto.replace("\\n", "\n").replace("\\t", "\t");
        assertThatThrownBy(() -> crearSut().leer(real))
                .isInstanceOfSatisfying(ErrorSintaxisArgdown.class, e -> {
                    assertThat(e.linea()).as("línea").isEqualTo(linea);
                    assertThat(e.columna()).as("columna").isEqualTo(columna);
                    assertThat(e.explicacion()).startsWith(explicacion);
                    assertThat(e.getMessage()).startsWith("Línea " + linea + ", columna " + columna + ": ");
                });
    }

    @Test
    void un_texto_vacio_pide_al_menos_una_conclusion() {
        assertThatThrownBy(() -> crearSut().leer("  \n\n "))
                .isInstanceOfSatisfying(ErrorSintaxisArgdown.class, e -> assertThat(e.explicacion()).contains("al menos una conclusión"));
    }

    @Test
    void escribir_rechaza_un_texto_que_no_se_podria_volver_a_leer() {
        DocumentoArgdown ambiguo = new DocumentoArgdown(List.of(new Enunciado(null, "Termina como marca #oculta", null, List.of())));
        assertThatThrownBy(() -> crearSut().escribir(ambiguo)).isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------------------------------------
    // Generador de árboles del subconjunto
    // ------------------------------------------------------------------------------------------------

    /** Árboles válidos al azar: títulos únicos, referencias a títulos definidos, argumentos con premisas. */
    static final class Generador {

        private static final String[] PALABRAS = {"pan", "sucursal", "vecinos", "año", "más", "tráfico", "ñandú", "¿por", "qué?", "«sí»",
                "30%", "x:y", "{llave}", "#etiqueta", "[nota]", "<menor>", "-3", "+2", "#oculta", "{peso: 3}", "café", "Ü"};
        private static final String[] FINALES = {"pan.", "vecinos", "año", "ventas.", "más", "ñ", "barrio", "cuota.", "sí"};

        private final Random azar;
        private final List<String> titulosDefinidos = new ArrayList<>();
        private int titulos;
        private int argumentos;
        private int enunciados;

        Generador(Random azar) {
            this.azar = azar;
        }

        DocumentoArgdown documento() {
            int n = 1 + azar.nextInt(3);
            List<Enunciado> raices = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                raices.add(enunciado(0, false));
            }
            return new DocumentoArgdown(raices);
        }

        private Enunciado enunciado(int nivel, boolean premisaDeArgumento) {
            enunciados++;
            List<Relacion> relaciones = new ArrayList<>();
            if (nivel < 4) {
                int hijos = azar.nextInt(nivel == 0 ? 4 : 3);
                for (int i = 0; i < hijos && enunciados < 40; i++) {
                    relaciones.add(relacion(nivel + 1));
                }
            }
            if (!titulosDefinidos.isEmpty() && nivel > 0 && azar.nextInt(5) == 0) {
                String titulo = titulosDefinidos.get(azar.nextInt(titulosDefinidos.size()));
                return new Enunciado(titulo, null, null, relaciones);
            }
            String titulo = azar.nextBoolean() ? "T" + (++titulos) + (azar.nextBoolean() ? " Ñ" : "") : null;
            if (titulo != null) {
                titulosDefinidos.add(titulo);
            }
            Marca marca = switch (azar.nextInt(4)) {
                case 0 -> Marca.OCULTA;
                case 1 -> Marca.ASUMIBLE;
                default -> null;
            };
            return new Enunciado(titulo, texto(titulo == null), marca, relaciones);
        }

        private Relacion relacion(int nivel) {
            Relacion.Tipo tipo = azar.nextBoolean() ? Relacion.Tipo.APOYO : Relacion.Tipo.ATAQUE;
            Integer peso = azar.nextInt(3) == 0 ? azar.nextInt(10) : null;
            Elemento destino;
            if (nivel < 4 && azar.nextInt(4) == 0) {
                List<Relacion> premisas = new ArrayList<>();
                int n = 1 + azar.nextInt(3);
                for (int i = 0; i < n; i++) {
                    premisas.add(new Relacion(Relacion.Tipo.APOYO, null, enunciado(nivel + 1, true)));
                }
                destino = new ArgumentoArgdown("Argumento " + (++argumentos), azar.nextBoolean() ? texto(false) : null, premisas);
            } else {
                destino = enunciado(nivel, false);
            }
            return new Relacion(tipo, peso, destino);
        }

        /** Palabras con signos en el medio y una palabra segura al final; sin título no empieza con [, <, + ni -. */
        private String texto(boolean sinTitulo) {
            StringBuilder sb = new StringBuilder(sinTitulo ? "Dicen" : PALABRAS[azar.nextInt(PALABRAS.length)]);
            int n = azar.nextInt(6);
            for (int i = 0; i < n; i++) {
                sb.append(' ').append(PALABRAS[azar.nextInt(PALABRAS.length)]);
            }
            return sb.append(' ').append(FINALES[azar.nextInt(FINALES.length)]).toString();
        }
    }
}
