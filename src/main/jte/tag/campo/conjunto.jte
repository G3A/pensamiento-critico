@import pensamiento.web.formulario.VistaCampo
@import pensamiento.web.formulario.Campo
@param VistaCampo c
<div class="campo campo-conjunto" data-campo="conjunto">
  <fieldset id="${c.id()}" aria-describedby="${c.describedBy()}" aria-invalid="${c.tieneError() ? "true" : null}">
    <legend>${c.etiqueta()}</legend>
    @for(Campo.Opcion o : c.opciones())
      <label class="casilla"><input type="checkbox" name="${c.nombre()}" value="${o.valor()}" checked="${c.elegido(o.valor())}"> ${o.etiqueta()}</label>
    @endfor
  </fieldset>
  @template.tag.campo.mensajes(c = c)
</div>
