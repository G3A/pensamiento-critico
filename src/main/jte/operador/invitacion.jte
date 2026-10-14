@import pensamiento.web.Pagina
@import pensamiento.web.operador.ControladorInvitacion.VistaInvitacion
@param Pagina pagina
@param VistaInvitacion v
<%-- Canje de la invitación del operador (hito 8): crea la cuenta del primer administrador de la institución. --%>
@template.layout(pagina = pagina, contenido = @`
  <section class="bloqueo-pagina">
    <form class="tarjeta formulario-bloqueo" method="post" action="/${v.ruta()}/invitacion">
      <input type="hidden" name="_csrf" value="${pagina.csrf()}">
      <h1>Tengo una invitación</h1>
      <p class="detalle">El operador del servicio te dio un código de 8 caracteres para ser quien administra esta institución.
        Escríbelo, elige tu nombre y tu contraseña (12 caracteres o más; una frase sirve). El código sirve una sola vez.</p>
      @if(v.error().isPresent())
        <p class="error" role="alert">${v.error().get()}</p>
      @endif
      <label for="codigo">Código de invitación</label>
      <input id="codigo" name="codigo" type="text" autocomplete="one-time-code" autocapitalize="characters" spellcheck="false"
             inputmode="text" maxlength="12" required autofocus placeholder="7KQ2-M9XD">
      <label for="nombre">Tu nombre</label>
      <input id="nombre" name="nombre" type="text" maxlength="60" autocomplete="username" required value="${v.nombreEscrito()}">
      <label for="contrasena">Tu contraseña (12 caracteres o más)</label>
      <input id="contrasena" name="contrasena" type="password" minlength="12" maxlength="128" autocomplete="new-password" required>
      <label for="repetida">Escríbela otra vez</label>
      <input id="repetida" name="repetida" type="password" minlength="12" maxlength="128" autocomplete="new-password" required>
      <button type="submit" class="boton">Crear mi cuenta</button>
    </form>
  </section>
`)
