@import pensamiento.web.formulario.VistaCampo
@import pensamiento.web.formulario.LectorFormulario
@param VistaCampo c
<fieldset class="campo campo-propuestas" data-campo="propuestas" id="${c.id()}" aria-describedby="${c.describedBy()}">
  <legend>${c.etiqueta()} <span class="conteo">(${c.propuestas().size()})</span></legend>
  @template.tag.campo.mensajes(c = c)
  @if(c.propuestas().isEmpty())
    <p class="detalle">Todavía no hay propuestas del modelo. Lo que propone el modelo no cuenta hasta que lo adoptas.</p>
  @else
    <ol class="propuestas" aria-label="${c.etiqueta()}">
      @for(VistaCampo.VistaPropuesta p : c.propuestas())
        <li class="propuesta ${p.adoptada() ? "propuesta-adoptada" : "propuesta-sin-adoptar"}" id="${c.id()}-${p.codigo()}" data-propuesta="${p.codigo()}">
          @for(String sub : LectorFormulario.CAMPOS_PROPUESTA)
            <input type="hidden" name="${c.nombre()}[${p.indice()}].${sub}"
                   value="${p.campo(sub)}">
          @endfor
          @if(p.adoptada())
            <input type="hidden" name="${c.nombre()}[${p.indice()}].adoptada" value="true">
          @endif
          <p class="rotulo-propuesta"><strong>${p.codigo()}</strong>
            @if(p.adoptada())
              <span class="chip chip-ok">adoptada por ti · cuenta</span>
            @else
              <span class="chip chip-pendiente">propuesta del modelo · sin adoptar · no cuenta</span>
            @endif
            ${p.rotulo()}</p>
          <p class="valor-propuesto">${p.valor()}</p>
          @if(!p.porque().isBlank())
            <p class="porque detalle">Por qué, según el modelo: ${p.porque()}</p>
          @endif
          @if(!p.adoptada() && c.accionUrl() != null)
            <button type="button" class="boton boton-secundario boton-chico adoptar"
                    hx-post="${c.accionUrl()}" hx-target="${c.destino()}" hx-swap="outerHTML" hx-include="${c.destino()}"
                    hx-vals="${c.accionAdoptar(p.codigo())}" aria-label="Adoptar ${p.codigo()}">Adoptar ${p.codigo()}</button>
          @endif
        </li>
      @endfor
    </ol>
  @endif
</fieldset>
