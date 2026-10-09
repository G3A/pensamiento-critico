@param String turno
@param String destino
<div class="espera" hx-ext="sse" sse-connect="/ia/turnos/${turno}/flujo" sse-close="fin" aria-busy="true" data-turno="${turno}">
  <p class="estado-espera" role="status">
    <span class="indicador" aria-hidden="true"></span>
    <strong>Etiquetando el pasaje con el modelo</strong>
    <span class="tiempo" sse-swap="tiempo">0 s</span>
    <span class="detalle">· en tu máquina tarda entre 5 y 60 segundos</span>
  </p>
  <div class="provisional" sse-swap="token" hx-swap="beforeend" aria-live="off"></div>
  <button type="button" class="boton boton-secundario boton-chico" hx-post="/verificar/turnos/${turno}/cancelar" hx-target="#${destino}"
          hx-swap="innerHTML" data-accion="cancelar">Cancelar</button>
  <div class="fin-espera" sse-swap="fin" hx-target="#${destino}" hx-swap="innerHTML"></div>
</div>
