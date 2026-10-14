@import pensamiento.web.seguridad.RespuestasSesion.ModeloEntrada
@param ModeloEntrada m
<%-- Una sola forma de entrar (hito 9, RF-02a): la misma en la página de cada institución y en el overlay de sesión expirada.
     Sin lista de nombres. Enter entra como quien escribió su nombre. El overlay manda "persona": solo sigue la misma persona. --%>
!{var p = m.overlay() ? "overlay-" : "";}
<form class="tarjeta formulario-entrar" method="post" action="/sesion"
      hx-post="${m.overlay() ? "/sesion" : null}" hx-target="${m.overlay() ? "#bloqueo" : null}" hx-swap="${m.overlay() ? "innerHTML" : null}">
  <input type="hidden" name="_csrf" value="${m.csrf()}">
  <input type="hidden" name="institucion" value="${m.ruta()}">
  @if(!m.volver().isEmpty())
    <input type="hidden" name="volver" value="${m.volver()}">
  @endif
  @if(m.persona().isPresent())
    <input type="hidden" name="persona" value="${m.persona().get()}">
  @endif
  @if(m.overlay())
    <h2 id="titulo-entrar">Entra de nuevo para seguir</h2>
    <p>Pasaron 10 minutos sin actividad y la sesión se cerró. Lo que estabas escribiendo sigue en la página: entra con tu
      nombre y tu contraseña y continúa. Solo puede seguir la misma persona.</p>
  @else
    <h1 id="titulo-entrar">Entrar</h1>
  @endif
  @if(m.aviso().isPresent())
    <p class="aviso" role="status">${m.aviso().get()}</p>
  @endif
  @if(m.error().isPresent())
    <p class="error" role="alert" id="${p}error-entrar">${m.error().get()}</p>
  @endif
  <label for="${p}nombre">Tu nombre</label>
  <input id="${p}nombre" name="nombre" type="text" autocomplete="username" autocapitalize="words" spellcheck="false" maxlength="60"
         required value="${m.nombre()}" autofocus="${m.nombre().isEmpty()}"
         aria-describedby="${m.error().isPresent() ? p + "error-entrar" : null}">
  <label for="${p}contrasena">Tu contraseña</label>
  <input id="${p}contrasena" name="contrasena" type="password" autocomplete="current-password" maxlength="128" required
         autofocus="${!m.nombre().isEmpty()}">
  <button type="submit" class="boton boton-grande">Entrar</button>
  @if(!m.overlay())
    <p class="entrar-enlaces">
      <a href="/${m.ruta()}/codigo">Tengo un código</a>
      <a href="/${m.ruta()}/sin-cuenta">¿No tienes cuenta?</a>
    </p>
  @endif
</form>
