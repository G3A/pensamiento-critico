package pensamiento.contrato;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.Competencia;
import pensamiento.nucleo.IntentoDojo;
import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RegistroAuditoria;
import pensamiento.nucleo.puertos.RepositorioAula;
import pensamiento.testutil.fakes.FakeRegistroAuditoria;
import pensamiento.testutil.fakes.FakeRepositorioAula;
import pensamiento.testutil.fakes.FakeRepositorioDojo;
import pensamiento.testutil.fakes.FakeRepositorioUsuarios;

class FakeRepositorioAulaContractTest extends RepositorioAulaContract {

    private final FakeRepositorioUsuarios usuarios = new FakeRepositorioUsuarios();
    private final FakeRepositorioDojo dojo = new FakeRepositorioDojo();
    private final FakeRegistroAuditoria auditoria = new FakeRegistroAuditoria();
    private final FakeRepositorioAula aula = new FakeRepositorioAula(usuarios, dojo, auditoria);
    private final Escenario escenario;

    FakeRepositorioAulaContractTest() {
        UUID institucion = UUID.randomUUID();
        UUID otra = UUID.randomUUID();
        escenario = new Escenario(institucion, cuenta(institucion, "administrador", Usuario.RolGlobal.ADMINISTRADOR),
                cuenta(institucion, "docente A", Usuario.RolGlobal.DOCENTE), cuenta(institucion, "docente B", Usuario.RolGlobal.DOCENTE),
                cuenta(institucion, "persona", Usuario.RolGlobal.PERSONA), otra, cuenta(otra, "administrador", Usuario.RolGlobal.ADMINISTRADOR));
    }

    private UUID cuenta(UUID institucion, String nombre, Usuario.RolGlobal rol) {
        return usuarios.crear(Usuario.nueva(UUID.randomUUID(), institucion, nombre, rol)).orElseThrow().id();
    }

    @Override
    protected Escenario escenario() {
        return escenario;
    }

    @Override
    protected RepositorioAula aula() {
        return aula;
    }

    @Override
    protected void responde(UUID estudiante, IntentoDojo intento, Competencia competencia) {
        dojo.guardar(estudiante, escenario.institucion(), intento, competencia);
    }

    @Override
    protected void entro(UUID usuario, Instant cuando) {
        auditoria.registrar(new RegistroAuditoria.Evento(Optional.of(usuario), escenario.institucion(), RegistroAuditoria.Accion.ENTRAR, "usuario",
                Optional.of(usuario), cuando));
    }
}
