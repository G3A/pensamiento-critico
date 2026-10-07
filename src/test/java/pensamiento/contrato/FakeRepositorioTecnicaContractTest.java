package pensamiento.contrato;

import java.util.List;

import pensamiento.nucleo.Familia;
import pensamiento.nucleo.Tecnica;
import pensamiento.nucleo.puertos.RepositorioTecnica;
import pensamiento.testutil.fakes.FakeRepositorioTecnica;

class FakeRepositorioTecnicaContractTest extends RepositorioTecnicaContract {

    private FakeRepositorioTecnica fake = new FakeRepositorioTecnica();

    @Override
    protected RepositorioTecnica crearSut() {
        return fake;
    }

    @Override
    protected void dadoQueExisten(List<Familia> familias, List<Tecnica> tecnicas) {
        fake = new FakeRepositorioTecnica();
        familias.forEach(fake::agregarFamilia);
        tecnicas.forEach(fake::agregar);
    }

    @Override
    protected void dadoQueNoExiste(pensamiento.nucleo.IdTecnica id) {
        fake.quitar(id);
    }

    @Override
    protected void dadoQueExistenEjemplos(List<pensamiento.nucleo.Ejemplo> ejemplos) {
        ejemplos.forEach(fake::agregarEjemplo);
    }

    @Override
    protected void dadoQueExisteRelacion(pensamiento.nucleo.RelacionTecnica relacion) {
        fake.agregarRelacion(relacion);
    }
}
