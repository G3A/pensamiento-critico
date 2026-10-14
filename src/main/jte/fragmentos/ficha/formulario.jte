@import pensamiento.web.tecnicas.VistaFormulario
@import pensamiento.web.formulario.VistaCampo
@param VistaFormulario f
@param boolean oob = false
<form id="${f.id()}" class="formulario-tecnica" hx-post="/tecnicas/${f.tecnica()}/evaluar" hx-target="#${f.idResultado()}" hx-swap="innerHTML"
      hx-sync="this:replace" novalidate aria-labelledby="${f.id()}-titulo" hx-swap-oob="${oob ? "true" : null}"
      data-desde-ejemplo="${f.desdeEjemplo() ? "true" : null}" data-con-errores="${f.tieneErrores() ? "true" : null}">
  <%-- Hito 9: la entrada se pliega a "Editar entrada" después de evaluar (app.js cierra el details al llegar el resultado). --%>
  <details class="entrada" open>
  <summary><h2 id="${f.id()}-titulo" class="subtitulo"><span class="al-abrir">Entrada</span><span class="al-plegar">Editar entrada</span></h2></summary>
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
  @if(f.expediente() != null)
    <input type="hidden" name="_expediente" value="${f.expediente()}">
  @endif
  @for(VistaCampo c : f.campos())
    @template.tag.campo.campo(c = c)
  @endfor
  @if(f.modelo().pide())
    @template.fragmentos.ficha.pedir-modelo(f = f, ocupado = false)
  @endif
  </details>
  <div class="acciones">
    <button type="submit" class="boton" hx-disabled-elt="this" data-accion="evaluar">Evaluar</button>
    <button type="button" class="boton boton-secundario" hx-post="/tecnicas/${f.tecnica()}/ejecuciones" hx-target="#${f.idResultado()}"
            hx-swap="innerHTML" hx-disabled-elt="this" hx-sync="closest form:drop" data-accion="guardar">Guardar en historial</button>
    @if(f.tieneErrores())
      <p class="resumen-errores" role="alert"><span class="chip chip-error">no se pudo</span> Revisa el formulario: ${f.resumenErrores()}.
        @if(!f.conError().isEmpty())<a href="#${f.conError().getFirst().id()}" data-ir-a-error>Ir al primero</a>@endif</p>
    @endif
    <p id="${f.idEstado()}" class="estado-guardado" role="status"></p>
  </div>
</form>
