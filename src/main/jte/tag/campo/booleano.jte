@import pensamiento.web.formulario.VistaCampo
@param VistaCampo c
<div class="campo campo-booleano" data-campo="booleano">
  <input type="hidden" name="${c.nombre()}" value="false">
  <label class="interruptor" for="${c.id()}">
    <input type="checkbox" role="switch" id="${c.id()}" name="${c.nombre()}" value="true" checked="${c.marcado()}"
           aria-describedby="${c.describedBy()}">
    <span>${c.etiqueta()}</span>
  </label>
  @template.tag.campo.mensajes(c = c)
</div>
