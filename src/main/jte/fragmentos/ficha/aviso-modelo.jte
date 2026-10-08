@import pensamiento.web.tecnicas.VistaFormulario
@param VistaFormulario f
@param String mensaje
<p class="aviso-ia" role="status"><span class="chip chip-aviso">sin modelo</span> ${mensaje}</p>
@template.fragmentos.ficha.pedir-modelo(f = f, ocupado = false, oob = true)
