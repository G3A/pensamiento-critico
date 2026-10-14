@import pensamiento.web.Pagina
@import pensamiento.web.usuarios.ControladorCuenta.VistaCuenta
@param Pagina pagina
@param VistaCuenta v
<%-- Hito 9: quien entró con el PIN de las versiones anteriores elige su contraseña antes de seguir. --%>
@template.layout(pagina = pagina, contenido = @`
  <section class="entrar-pagina">
    <form class="tarjeta formulario-entrar" method="post" action="/cuenta/contrasena-nueva">
      <input type="hidden" name="_csrf" value="${pagina.csrf()}">
      <h1>Elige tu contraseña</h1>
      <p>Hola, ${v.yo().nombre()}. La app ya no usa PIN: entraste con el tuyo por última vez. Elige una contraseña de
        ${v.yo().rol().largoMinimoDeContrasena()} caracteres o más; una frase corta que recuerdes sirve.</p>
      @if(v.error().isPresent())
        <p class="error" role="alert">${v.error().get()}</p>
      @endif
      <label for="nueva">Tu contraseña nueva</label>
      <input id="nueva" name="nueva" type="password" autocomplete="new-password" minlength="${v.yo().rol().largoMinimoDeContrasena()}"
             maxlength="128" required autofocus>
      <label for="repetida">Escríbela otra vez</label>
      <input id="repetida" name="repetida" type="password" autocomplete="new-password" minlength="${v.yo().rol().largoMinimoDeContrasena()}"
             maxlength="128" required>
      <button type="submit" class="boton boton-grande">Guardar y seguir</button>
    </form>
  </section>
`)
