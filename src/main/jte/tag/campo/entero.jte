@import pensamiento.web.formulario.VistaCampo
@param VistaCampo c
<div class="campo campo-entero" data-campo="entero">
  <label for="${c.id()}">${c.etiqueta()}</label>
  <div class="entero-con-rango">
    <input type="number" id="${c.id()}" name="${c.nombre()}" value="${c.valor()}" min="${c.minimo()}" max="${c.maximo()}" step="1"
           inputmode="numeric" required="${c.obligatorio()}" aria-invalid="${c.tieneError() ? "true" : null}" aria-describedby="${c.describedBy()}">
    <input type="range" id="${c.id()}-rango" value="${c.valor()}" min="${c.minimo()}" max="${c.maximo()}" step="1" tabindex="-1"
           aria-hidden="true" data-espejo="${c.id()}">
    <output for="${c.id()}" class="valor-visible">${c.valor()}</output>
  </div>
  @template.tag.campo.mensajes(c = c)
</div>
