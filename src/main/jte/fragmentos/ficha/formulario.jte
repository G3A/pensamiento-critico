@import pensamiento.web.tecnicas.VistaFormulario
@import pensamiento.web.formulario.VistaCampo
@param VistaFormulario f
@param boolean oob = false
<form id="${f.id()}" class="formulario-tecnica" hx-post="/tecnicas/${f.tecnica()}/evaluar" hx-target="#${f.idResultado()}" hx-swap="innerHTML"
      hx-sync="this:replace" novalidate aria-labelledby="${f.id()}-titulo" hx-swap-oob="${oob ? "true" : null}">
  <h2 id="${f.id()}-titulo" class="subtitulo">Entrada</h2>
  <p class="origen-config">Con ${f.origenConfig()}: ${f.resumenConfig()}</p>
  @if(!f.erroresConfig().isEmpty())
    <div class="error-campo" role="alert">
      @for(String error : f.erroresConfig())
        <p><span class="chip chip-error">error</span> ${error}</p>
      @endfor
    </div>
  @endif
  @for(VistaFormulario.Oculto o : f.config())
    <input type="hidden" name="${o.nombre()}" value="${o.valor()}">
  @endfor
  <input type="hidden" id="clave-${f.tecnica()}" name="_clave" value="${f.clave()}">
  <input type="hidden" name="_origen" value="${f.origenConfig()}">
  @for(VistaCampo c : f.campos())
    @template.tag.campo.campo(c = c)
  @endfor
  <div class="acciones">
    <button type="submit" class="boton" hx-disabled-elt="this" data-accion="evaluar">Evaluar</button>
    <button type="button" class="boton boton-secundario" hx-post="/tecnicas/${f.tecnica()}/ejecuciones" hx-target="#${f.idResultado()}"
            hx-swap="innerHTML" hx-disabled-elt="this" hx-sync="closest form:drop" data-accion="guardar">Guardar en historial</button>
  </div>
</form>
