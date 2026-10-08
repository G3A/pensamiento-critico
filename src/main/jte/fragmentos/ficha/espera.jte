@import pensamiento.web.tecnicas.VistaFormulario
@param VistaFormulario f
@param String turno
@param String tecnica
<div class="espera" hx-ext="sse" sse-connect="/ia/turnos/${turno}/flujo" sse-close="fin" aria-busy="true" data-turno="${turno}">
  <p class="estado-espera" role="status">
    <span class="indicador" aria-hidden="true"></span>
    <strong>${tecnica} · esperando al modelo</strong>
    <span class="tiempo" sse-swap="tiempo">0 s</span>
    <span class="detalle">· en tu máquina cada respuesta tarda entre 5 y 60 segundos</span>
  </p>
  <div class="provisional" sse-swap="token" hx-swap="beforeend" aria-live="off"></div>
  <p class="detalle">Texto provisional: la versión final reemplaza este cuadro.</p>
  <button type="button" class="boton boton-secundario boton-chico" hx-post="/ia/turnos/${turno}/cancelar" hx-target="#${f.idEspera()}"
          hx-swap="innerHTML" data-accion="cancelar">Cancelar</button>
  <div class="fin-espera" sse-swap="fin" hx-target="#${f.idEspera()}" hx-swap="innerHTML"></div>
</div>
@template.fragmentos.ficha.pedir-modelo(f = f, ocupado = true, oob = true)
