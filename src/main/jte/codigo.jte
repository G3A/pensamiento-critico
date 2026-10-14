@import pensamiento.web.Pagina
@import pensamiento.web.usuarios.ControladorCodigo.VistaCodigo
@param Pagina pagina
@param VistaCodigo v
<%-- "Tengo un código" (hito 9, RF-16 y RF-17): canjear el código de alta o de restablecimiento y elegir la contraseña. --%>
@template.layout(pagina = pagina, contenido = @`
  <section class="entrar-pagina">
    <form class="tarjeta formulario-entrar" method="post" action="/${v.ruta()}/codigo">
      <input type="hidden" name="_csrf" value="${pagina.csrf()}">
      <h1>Tengo un código</h1>
      <p class="detalle">Escribe el código de tu tarjeta (8 letras y números, por ejemplo 7KQ2-M9XD) y elige tu contraseña:
        10 caracteres o más (12 si administras). Una frase corta que recuerdes sirve. El código sirve una sola vez.</p>
      @if(v.error().isPresent())
        <p class="error" role="alert" id="error-codigo">${v.error().get()}</p>
      @endif
      <label for="codigo">Código</label>
      <input id="codigo" name="codigo" type="text" autocomplete="one-time-code" autocapitalize="characters" spellcheck="false"
             maxlength="12" required autofocus="${v.codigoEscrito().isEmpty()}" value="${v.codigoEscrito()}" placeholder="7KQ2-M9XD"
             aria-describedby="${v.error().isPresent() ? "error-codigo" : null}">
      <label for="contrasena">Tu contraseña nueva</label>
      <input id="contrasena" name="contrasena" type="password" minlength="10" maxlength="128" autocomplete="new-password" required
             autofocus="${!v.codigoEscrito().isEmpty()}">
      <label for="repetida">Escríbela otra vez</label>
      <input id="repetida" name="repetida" type="password" minlength="10" maxlength="128" autocomplete="new-password" required>
      <button type="submit" class="boton boton-grande">Guardar mi contraseña</button>
      <p class="entrar-enlaces"><a href="/${v.ruta()}">Ya tengo contraseña: entrar</a></p>
    </form>
  </section>
`)
