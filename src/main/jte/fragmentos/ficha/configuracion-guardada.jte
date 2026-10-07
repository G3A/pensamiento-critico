@import pensamiento.web.tecnicas.ControladorTecnicas.VistaConfiguracion
@import pensamiento.web.tecnicas.VistaFormulario
@param VistaConfiguracion c
@param VistaFormulario f
@template.fragmentos.ficha.configuracion(c = c)
@if(f != null)
  <p id="resumen-config-${c.tecnica()}" class="resumen-config" hx-swap-oob="true">Tu configuración: ${c.resumen()}</p>
  @template.fragmentos.ficha.formulario(f = f, oob = true)
@endif
