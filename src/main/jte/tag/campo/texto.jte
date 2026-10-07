@import pensamiento.web.formulario.VistaCampo
@param VistaCampo c
<div class="campo campo-texto" data-campo="texto">
  <label for="${c.id()}">${c.etiqueta()}@if(c.obligatorio()) <span class="obligatorio">(obligatorio)</span>@endif</label>
  @if(c.comoTextarea())
    <textarea id="${c.id()}" name="${c.nombre()}" rows="2" maxlength="${c.largoMaximo()}" required="${c.obligatorio()}"
              aria-invalid="${c.tieneError() ? "true" : null}" aria-describedby="${c.describedBy()}">${c.valor()}</textarea>
  @else
    <input type="text" id="${c.id()}" name="${c.nombre()}" value="${c.valor()}" maxlength="${c.largoMaximo()}" required="${c.obligatorio()}"
           aria-invalid="${c.tieneError() ? "true" : null}" aria-describedby="${c.describedBy()}">
  @endif
  @template.tag.campo.mensajes(c = c)
</div>
