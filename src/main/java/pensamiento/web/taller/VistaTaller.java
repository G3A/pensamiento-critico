package pensamiento.web.taller;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import gg.jte.Content;

import pensamiento.nucleo.EstandarPrueba;
import pensamiento.tecnicas.f3.ResultadoFalacias;

/**
 * Lo que pinta el formulario del Taller (#form-taller): el texto, el estándar, lo que completa el panel Toulmin,
 * los errores por campo y, si ya se evaluó, los tres paneles con sus resultados.
 *
 * @param textoEvaluado el texto de la última evaluación: si cambió, las confirmaciones de marcas ya no valen
 * @param marcas        las marcas de T13 para confirmar con casillas; vacía si no se evaluó
 */
public record VistaTaller(String argdown, EstandarPrueba estandar, String respaldo, String fuenteRespaldo, String calificador,
                          String textoEvaluado, String clave, Map<String, String> errores, Optional<Content> mapa, Optional<Content> toulmin,
                          Optional<Content> falacias, List<ResultadoFalacias.Marca> marcas, List<String> confirmadas, String errorToulmin) {

    public static final String ID_FORMULARIO = "form-taller";

    public List<EstandarPrueba> estandares() {
        return List.of(EstandarPrueba.values());
    }

    public boolean evaluado() {
        return mapa.isPresent();
    }

    public String error(String campo) {
        return errores.getOrDefault(campo, "");
    }

    public boolean tieneError(String campo) {
        return errores.containsKey(campo);
    }

    public boolean confirmada(ResultadoFalacias.Marca m) {
        return confirmadas.contains(m.codigo());
    }

    public String vacio(String texto) {
        return texto == null ? "" : texto;
    }
}
