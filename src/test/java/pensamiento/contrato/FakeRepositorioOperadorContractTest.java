package pensamiento.contrato;

import java.time.Instant;
import java.util.UUID;

import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RepositorioInstituciones;
import pensamiento.nucleo.puertos.RepositorioOperador;
import pensamiento.testutil.fakes.FakeReloj;
import pensamiento.testutil.fakes.FakeRepositorioInstituciones;
import pensamiento.testutil.fakes.FakeRepositorioOperador;
import pensamiento.testutil.fakes.FakeRepositorioUsuarios;

class FakeRepositorioOperadorContractTest extends RepositorioOperadorContract {

    private final FakeReloj reloj = new FakeReloj();
    private final FakeRepositorioInstituciones instituciones = new FakeRepositorioInstituciones();
    private final FakeRepositorioUsuarios usuarios = new FakeRepositorioUsuarios();
    private final FakeRepositorioOperador fake = new FakeRepositorioOperador(instituciones, usuarios, reloj);

    @Override
    protected RepositorioOperador crearSut() {
        return fake;
    }

    @Override
    protected RepositorioInstituciones instituciones() {
        return instituciones;
    }

    @Override
    protected UUID operador() {
        fake.crearSiNoHay("operador", "$argon2id$hash");
        return fake.porNombre("operador").orElseThrow().id();
    }

    @Override
    protected void agregarCuenta(UUID institucion) {
        usuarios.guardar(new Usuario(UUID.randomUUID(), institucion, "administrador", "h", Usuario.RolGlobal.ADMINISTRADOR, true));
    }

    @Override
    protected Instant ahora() {
        return reloj.ahora();
    }
}
