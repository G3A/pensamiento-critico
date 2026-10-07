@import pensamiento.web.seguridad.RespuestasSesion.ModeloBloqueo
@param ModeloBloqueo bloqueo
<%-- Fragmento "¿Quién eres?": como overlay cuando la sesión expiró en medio de un formulario (el borrador queda intacto). --%>
<div class="${bloqueo.overlay() ? "overlay" : ""}" role="dialog" aria-modal="${bloqueo.overlay()}" aria-labelledby="titulo-bloqueo">
  <form class="tarjeta formulario-bloqueo" method="post" action="/sesion" hx-post="/sesion" hx-target="#bloqueo" hx-swap="innerHTML">
    <input type="hidden" name="_csrf" value="${bloqueo.csrf()}">
    <h2 id="titulo-bloqueo">¿Quién eres?</h2>
    @if(bloqueo.overlay())
      <p>Pasaron 10 minutos sin actividad y la sesión se cerró. Lo que estabas escribiendo sigue en la página: entra de nuevo y continúa.</p>
    @endif
    @if(bloqueo.error() != null)
      <p class="error" role="alert">${bloqueo.error()}</p>
    @endif
    <label for="nombre-bloqueo">Tu nombre</label>
    <input id="nombre-bloqueo" name="nombre" type="text" autocomplete="username" required autofocus>
    <label for="pin-bloqueo">Tu PIN</label>
    <input id="pin-bloqueo" name="pin" type="password" inputmode="numeric" pattern="[0-9]{4,8}" autocomplete="current-password" required>
    <button type="submit" class="boton">Entrar</button>
  </form>
</div>
