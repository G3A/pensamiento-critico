@import pensamiento.web.consejero.VistaConsejero
@param VistaConsejero.Sesion v
@param boolean oob
<div id="entrada-consejero" class="entrada-consejero" hx-swap-oob="${oob ? "true" : null}">
  @if(v.cerrada())
    <p class="detalle" role="status"><span class="chip chip-ok">cerrada</span> Esta sesión está cerrada: queda en solo lectura.</p>
  @elseif(v.lista())
    <form class="cerrar-sesion" hx-post="${v.base()}/cerrar" hx-target="#mensaje-consejero" hx-swap="innerHTML" novalidate aria-labelledby="cerrar-titulo">
      <h2 id="cerrar-titulo" class="subtitulo">Cerrar la sesión</h2>
      <fieldset class="reflexion-cierre">
        <legend>Reflexión (T47 · Reflexión estructurada)${v.reflexion().obligatoria() ? ": responde al menos una pregunta" : ", opcional"}</legend>
        <label for="reflexion">¿Qué cambió en lo que piensas?</label>
        <textarea id="reflexion" name="reflexion" maxlength="600"></textarea>
        @for(var q : v.reflexion().preguntas())
          <label for="r-${q.toString()}">${q.texto()}</label>
          <textarea id="r-${q.toString()}" name="${VistaConsejero.Reflexion.campo(q)}" maxlength="600"></textarea>
        @endfor
      </fieldset>
      <label for="confianzaDespues">¿Qué tan seguro estás ahora? (0 a 100, opcional)</label>
      <input id="confianzaDespues" name="confianzaDespues" type="number" min="0" max="100">
      <fieldset>
        <legend>Si cambió, ¿qué la movió?</legend>
        <label><input type="radio" name="causa" value="evidencia"> Evidencia</label>
        <label><input type="radio" name="causa" value="steelman"> El mejor argumento contrario</label>
        <label><input type="radio" name="causa" value="manual" checked> Otra cosa</label>
      </fieldset>
      @if(!v.comprobables().isEmpty())
        <fieldset>
          <legend>¿Qué peldaños comprobaste con otra persona o con un dato?</legend>
          @for(String c : v.comprobables())
            <label><input type="checkbox" name="comprobados" value="${c}"> ${c}</label>
          @endfor
        </fieldset>
      @endif
      <button type="submit" class="boton" data-accion="cerrar-sesion">Cerrar y guardar</button>
    </form>
  @else
    <form class="consejero-entrada" hx-post="${v.base()}/turnos" hx-target="#dialogo" hx-swap="beforeend" novalidate
          aria-label="Tu respuesta">
      <label for="texto-respuesta" class="solo-lector">Tu respuesta</label>
      <textarea id="texto-respuesta" name="texto" maxlength="600" required disabled="${v.redactando()}"
                placeholder="${v.redactando() ? "Espera la pregunta del Consejero…" : "Escribe tu respuesta…"}"></textarea>
      <button type="submit" class="boton" disabled="${v.redactando()}" data-accion="enviar">Enviar</button>
      <button type="button" class="boton boton-secundario" hx-post="${v.base()}/cierre" hx-target="#dialogo" hx-swap="beforeend"
              disabled="${v.redactando()}" data-accion="ir-al-cierre">Ir al cierre</button>
    </form>
  @endif
</div>
