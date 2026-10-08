package pensamiento.contrato;

import pensamiento.argdown.ParserArgdown;
import pensamiento.nucleo.puertos.Argdown;

/** El adaptador real del puerto Argdown: puro, sin red ni base, por eso corre en cada PR. */
class ParserArgdownContractTest extends ArgdownContract {

    @Override
    protected Argdown crearSut() {
        return new ParserArgdown();
    }
}
