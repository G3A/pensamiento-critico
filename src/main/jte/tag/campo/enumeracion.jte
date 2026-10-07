@import pensamiento.web.formulario.VistaCampo
@import pensamiento.web.formulario.Campo
@param VistaCampo c
<div class="campo campo-enumeracion" data-campo="enumeracion">
  @if(c.comoRadios())
    <fieldset id="${c.id()}" aria-describedby="${c.describedBy()}" aria-invalid="${c.tieneError() ? "true" : null}">
      <legend>${c.etiqueta()}</legend>
      <div class="radios">
        @for(Campo.Opcion o : c.opciones())
          <label class="radio"><input type="radio" name="${c.nombre()}" value="${o.valor()}" checked="${c.elegido(o.valor())}" required="${c.obligatorio()}"> ${o.etiqueta()}</label>
        @endfor
      </div>
    </fieldset>
  @else
    <label for="${c.id()}">${c.etiqueta()}</label>
    <select id="${c.id()}" name="${c.nombre()}" required="${c.obligatorio()}" aria-invalid="${c.tieneError() ? "true" : null}"
            aria-describedby="${c.describedBy()}">
      <option value="">Elige</option>
      @for(Campo.Opcion o : c.opciones())
        <option value="${o.valor()}" selected="${c.elegido(o.valor())}">${o.etiqueta()}</option>
      @endfor
    </select>
  @endif
  @template.tag.campo.mensajes(c = c)
</div>
