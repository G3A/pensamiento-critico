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
}
