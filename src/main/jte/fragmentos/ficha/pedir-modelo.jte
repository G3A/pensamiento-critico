@import pensamiento.web.tecnicas.VistaFormulario
@param VistaFormulario f
@param boolean ocupado
@param boolean oob = false
<div id="${f.idPedir()}" class="pedir-modelo" hx-swap-oob="${oob ? "true" : null}">
  @if(f.modelo().disponible() && !ocupado)
    <button type="button" class="boton boton-secundario" hx-post="/tecnicas/${f.tecnica()}/propuestas" hx-target="#${f.idEspera()}"
            hx-swap="innerHTML" hx-disabled-elt="this" hx-sync="closest form:drop" data-accion="proponer">Pedir propuestas al modelo</button>
  @else
    <button type="button" class="boton boton-secundario" disabled aria-disabled="true" data-accion="proponer">Pedir propuestas al modelo</button>
  @endif
  @if(ocupado)
    <span class="detalle">El modelo está trabajando; puedes cancelar en el cuadro de espera.</span>
  @elseif(f.modelo().disponible())
    <span class="detalle">Lo que proponga no cuenta hasta que lo adoptes.</span>
  @else
    <span class="detalle">El modelo no está disponible: sigues en modo plantillas.</span>
  @endif
  @if(f.modelo().experimental())
    <span class="chip chip-aviso">experimental</span>
    <span class="detalle">Las propuestas del modelo en esta técnica no cumplieron los umbrales del informe de evaluación: revísalas con más cuidado.</span>
  @endif
</div>
