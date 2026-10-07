@import pensamiento.web.formulario.VistaCampo
@param VistaCampo c
<div class="campo campo-lista-ordenada" data-campo="lista_ordenada">
  <fieldset id="${c.id()}" aria-describedby="${c.describedBy()}">
    <legend>${c.etiqueta()}</legend>
    <ol class="lista-ordenada">
      @for(int i = 0; i < c.valores().size(); i++)
        <li>
          <input type="hidden" name="${c.nombre()}[${i}]" value="${c.valores().get(i)}">
          <span>${c.etiquetaDe(c.valores().get(i))}</span>
          <button type="button" class="boton boton-secundario boton-chico" disabled="${i == 0}"
                  hx-post="${c.accionUrl()}" hx-target="${c.destino()}" hx-swap="outerHTML" hx-include="${c.destino()}"
                  hx-vals="${c.accion("subir", i)}" aria-label="Subir ${c.etiquetaDe(c.valores().get(i))}">Subir</button>
          <button type="button" class="boton boton-secundario boton-chico" disabled="${i == c.valores().size() - 1}"
                  hx-post="${c.accionUrl()}" hx-target="${c.destino()}" hx-swap="outerHTML" hx-include="${c.destino()}"
                  hx-vals="${c.accion("bajar", i)}" aria-label="Bajar ${c.etiquetaDe(c.valores().get(i))}">Bajar</button>
        </li>
      @endfor
    </ol>
  </fieldset>
  @template.tag.campo.mensajes(c = c)
</div>
