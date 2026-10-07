@import pensamiento.web.formulario.VistaCampo
@param VistaCampo c
<div class="campo campo-fecha" data-campo="fecha">
  <label for="${c.id()}">${c.etiqueta()}</label>
  <input type="date" id="${c.id()}" name="${c.nombre()}" value="${c.valor()}" required="${c.obligatorio()}"
         aria-invalid="${c.tieneError() ? "true" : null}" aria-describedby="${c.describedBy()}">
  @template.tag.campo.mensajes(c = c)
</div>
