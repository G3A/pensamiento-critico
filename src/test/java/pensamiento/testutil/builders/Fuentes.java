package pensamiento.testutil.builders;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.Fuente;

/** Object Mother de fuentes del universo ficticio: panadería, familia y barrio. */
public final class Fuentes {

    private Fuentes() {
    }

    public static Builder fuente() {
        return new Builder();
    }

    /** Metaanálisis de 2024, independiente, con acceso al original y CRAAP 21 (ejemplo de R01 del documento). */
    public static Fuente metaanalisis2024() {
        return fuente().titulo("Metaanálisis sobre ventas y tráfico peatonal, 2024")
                .tipo(Fuente.TipoFuente.SECUNDARIA)
                .diseno(Fuente.DisenoEstudio.REVISION_SISTEMATICA)
                .fecha(LocalDate.of(2024, 3, 1))
                .grupo("instituto-estudios")
                .independiente(true)
                .accesoOriginal(true)
                .craap(21)
                .build();
    }

    /** Testimonio de una vecina en 2025, no independiente, sin CRAAP (ejemplo de R01 del documento). */
    public static Fuente testimonioVecina2025() {
        return fuente().titulo("Lo que contó una vecina en la reunión")
                .tipo(Fuente.TipoFuente.TERCIARIA)
                .diseno(Fuente.DisenoEstudio.TESTIMONIO)
                .fecha(LocalDate.of(2025, 6, 10))
                .grupo("junta-vecinos")
                .independiente(false)
                .accesoOriginal(false)
                .build();
    }

    public static final class Builder {
        private UUID id = UUID.randomUUID();
        private String titulo = "Fuente de prueba";
        private Fuente.TipoFuente tipo = Fuente.TipoFuente.PRIMARIA;
        private Optional<Fuente.DisenoEstudio> diseno = Optional.empty();
        private Optional<LocalDate> fecha = Optional.empty();
        private Optional<String> grupo = Optional.empty();
        private boolean independiente = false;
        private boolean accesoOriginal = false;
        private Optional<Integer> craap = Optional.empty();

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder titulo(String titulo) { this.titulo = titulo; return this; }
        public Builder tipo(Fuente.TipoFuente tipo) { this.tipo = tipo; return this; }
        public Builder diseno(Fuente.DisenoEstudio diseno) { this.diseno = Optional.ofNullable(diseno); return this; }
        public Builder fecha(LocalDate fecha) { this.fecha = Optional.ofNullable(fecha); return this; }
        public Builder grupo(String grupo) { this.grupo = Optional.ofNullable(grupo); return this; }
        public Builder independiente(boolean v) { this.independiente = v; return this; }
        public Builder accesoOriginal(boolean v) { this.accesoOriginal = v; return this; }
        public Builder craap(Integer craap) { this.craap = Optional.ofNullable(craap); return this; }

        public Fuente build() {
            return new Fuente(id, titulo, tipo, diseno, fecha, grupo, independiente, accesoOriginal, craap);
        }
    }
}
