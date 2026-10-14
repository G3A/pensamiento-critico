@import pensamiento.web.formulario.VistaCampo
@param VistaCampo c
<%-- Hito 9: las pistas (filas "evidencias") se ven una a la vez en angosto, con "Siguiente pista" (#ancla y :target, sin JS). --%>
!{boolean unaALaVez = c.nombre().endsWith("evidencias") && c.filas().size() > 1;}
<fieldset class="campo campo-filas ${unaALaVez ? "una-a-la-vez" : ""}" data-campo="filas" id="${c.id()}" aria-describedby="${c.describedBy()}" aria-invalid="${c.tieneError() ? "true" : null}">
  <legend>${c.etiqueta()} <span class="conteo">(${c.filas().size()}@if(c.maximo() != null) de ${c.maximo()}@endif)</span></legend>
  @template.tag.campo.mensajes(c = c)
  <ol class="filas" aria-label="${c.etiqueta()}">
    @for(VistaCampo.Fila fila : c.filas())
      <li class="fila" id="${c.id()}-fila-${fila.indice()}" data-codigo="${fila.codigo()}">
        <span class="codigo-fila" aria-hidden="true">${fila.codigo()}</span>
        <div class="campos-fila">
          @for(VistaCampo sub : fila.campos())
            @template.tag.campo.campo(c = sub)
          @endfor
        </div>
        @if(unaALaVez)
          <p class="solo-angosto paso-pista">
            <span class="detalle">Pista ${fila.indice() + 1} de ${c.filas().size()}</span>
            @if(fila.indice() + 1 < c.filas().size())
              <a class="boton boton-secundario" href="#${c.id()}-fila-${fila.indice() + 1}">Siguiente pista</a>
            @else
              <span class="detalle">Es la última: toca «Evaluar».</span>
            @endif
          </p>
        @endif
        @if(fila.puedeQuitar() && c.accionUrl() != null)
          <button type="button" class="boton boton-secundario boton-chico quitar-fila"
                  hx-post="${c.accionUrl()}" hx-target="${c.destino()}" hx-swap="outerHTML" hx-include="${c.destino()}"
                  hx-vals="${c.accion("quitar", fila.indice())}" aria-label="Quitar ${fila.codigo()}">Quitar ${fila.codigo()}</button>
        @endif
      </li>
    @endfor
  </ol>
  @if(c.puedeAnadir() && c.accionUrl() != null)
    <button type="button" class="boton boton-secundario anadir-fila"
            hx-post="${c.accionUrl()}" hx-target="${c.destino()}" hx-swap="outerHTML" hx-include="${c.destino()}"
            hx-vals="${c.accion("anadir", -1)}">Añadir ${c.elemento()}</button>
  @endif
</fieldset>
