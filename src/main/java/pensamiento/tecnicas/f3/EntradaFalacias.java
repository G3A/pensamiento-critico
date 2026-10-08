package pensamiento.tecnicas.f3;

import java.util.List;

/**
 * Entrada de T13: el texto propio y los códigos de las marcas (M1, M2…) que la persona confirma como falacia.
 * Con el mismo texto y la misma configuración, los códigos no cambian.
 */
public record EntradaFalacias(String texto, List<String> confirmadas) {

    public EntradaFalacias {
        confirmadas = confirmadas == null ? List.of() : List.copyOf(confirmadas);
    }
}
