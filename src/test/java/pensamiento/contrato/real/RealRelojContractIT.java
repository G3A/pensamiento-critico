package pensamiento.contrato.real;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import pensamiento.contrato.RelojContract;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.web.ConfiguracionWeb;

/** El reloj real del sistema en la zona configurada. */
@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealRelojContractIT extends RelojContract {

    @Override
    protected Reloj crearSut() {
        return new ConfiguracionWeb().reloj("America/Bogota");
    }
}
