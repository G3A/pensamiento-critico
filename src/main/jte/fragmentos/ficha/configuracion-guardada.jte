@import pensamiento.web.tecnicas.ControladorTecnicas.VistaConfiguracion
@import pensamiento.web.tecnicas.VistaFormulario
@param VistaConfiguracion c
@param VistaFormulario f
@template.fragmentos.ficha.configuracion(c = c)
@if(f != null)
  <span id="resumen-config-${c.tecnica()}" class="detalle resumen-config" hx-swap-oob="true">${c.resumen()}</span>
  @template.fragmentos.ficha.formulario(f = f, oob = true)
@endif
