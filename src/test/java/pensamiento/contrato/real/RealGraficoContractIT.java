package pensamiento.contrato.real;

import java.time.Duration;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import pensamiento.contrato.GraficoContract;
import pensamiento.graficos.GraphvizProceso;
import pensamiento.nucleo.puertos.Grafico;

/** Graphviz real (dot) como proceso hijo con 5 segundos y -Gnslimit, dentro del compose. */
@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealGraficoContractIT extends GraficoContract {

    @Override
    protected Grafico crearSut() {
        return new GraphvizProceso("dot", Duration.ofSeconds(5), 50, 4);
    }
}
