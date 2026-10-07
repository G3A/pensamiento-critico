@import pensamiento.web.tecnicas.ControladorTecnicas.VistaConfiguracion
@import pensamiento.web.formulario.VistaCampo
@param VistaConfiguracion c
<form id="config-${c.tecnica()}" class="formulario-configuracion" hx-post="/tecnicas/${c.tecnica()}/configuracion"
      hx-include="#form-${c.tecnica()}" hx-target="this" hx-swap="outerHTML" novalidate>
  @for(VistaCampo campo : c.campos())
    @template.tag.campo.campo(c = campo)
  @endfor
  <div class="acciones">
    <button type="submit" class="boton" hx-disabled-elt="this">Guardar configuración</button>
    <button type="button" class="boton boton-secundario" hx-post="/tecnicas/${c.tecnica()}/configuracion/restablecer"
            hx-include="#form-${c.tecnica()}" hx-target="#config-${c.tecnica()}" hx-swap="outerHTML">Restablecer</button>
  </div>
  <p class="detalle" role="status">@if(!c.mensaje().isEmpty())${c.mensaje()} @endif Se guarda por usuario.
    @if(c.personalizada()) Es tu configuración; "Restablecer" vuelve a la del catálogo.@else Es la configuración del catálogo.@endif</p>
</form>
