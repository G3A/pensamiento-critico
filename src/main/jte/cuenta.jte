@import pensamiento.web.Pagina
@import pensamiento.web.usuarios.ControladorCuenta.VistaCuenta
@param Pagina pagina
@param VistaCuenta v
<%-- Mi cuenta (hito 9, RF-17): cambiar la contraseña propia dando la actual. --%>
@template.layout(pagina = pagina, contenido = @`
  <section class="cuenta-pagina">
    <h1>Mi cuenta</h1>
    <p>Entraste como <strong>${v.yo().nombre()}</strong> (${v.yo().rol().enBaseDeDatos()}).</p>
    @if(v.aviso().isPresent())
      <p class="aviso" role="status">${v.aviso().get()}</p>
    @endif
    @if(v.error().isPresent())
      <p class="error" role="alert">${v.error().get()}</p>
    @endif
    <form class="tarjeta formulario" method="post" action="/cuenta/contrasena">
      <input type="hidden" name="_csrf" value="${pagina.csrf()}">
      <h2>Cambiar mi contraseña</h2>
      <label for="actual">Tu contraseña actual</label>
      <input id="actual" name="actual" type="password" autocomplete="current-password" maxlength="128" required>
      <label for="nueva">Tu contraseña nueva (${v.yo().rol().largoMinimoDeContrasena()} caracteres o más; una frase sirve)</label>
      <input id="nueva" name="nueva" type="password" autocomplete="new-password" minlength="${v.yo().rol().largoMinimoDeContrasena()}" maxlength="128" required>
      <label for="repetida">Escríbela otra vez</label>
      <input id="repetida" name="repetida" type="password" autocomplete="new-password" minlength="${v.yo().rol().largoMinimoDeContrasena()}" maxlength="128" required>
      <button type="submit" class="boton">Cambiar mi contraseña</button>
    </form>
    <p class="detalle">Si olvidas tu contraseña, pide un código para restablecerla a quien administra la app en tu institución.</p>
  </section>
`)
