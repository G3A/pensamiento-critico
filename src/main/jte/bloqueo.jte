@import pensamiento.web.Pagina
@import pensamiento.web.seguridad.RespuestasSesion.ModeloBloqueo
@import pensamiento.nucleo.Usuario
@import java.util.List
@param Pagina pagina
@param ModeloBloqueo bloqueo
@param List<Usuario> personas
@param String volver
@template.layout(pagina = pagina, contenido = @`
  <section class="bloqueo-pagina">
    <form class="tarjeta formulario-bloqueo" method="post" action="/sesion">
      <input type="hidden" name="_csrf" value="${bloqueo.csrf()}">
      <input type="hidden" name="volver" value="${volver}">
      <h1>¿Quién eres?</h1>
      <p class="detalle">Escribe tu PIN y elige tu nombre. Cada persona tiene su cuenta y su PIN; las crea el administrador.</p>
      @if(bloqueo.error() != null)
        <p class="error" role="alert">${bloqueo.error()}</p>
      @endif
      <label for="pin">Tu PIN</label>
      <input id="pin" name="pin" type="password" inputmode="numeric" pattern="[0-9]{4,8}" autocomplete="current-password" required autofocus>
      @if(personas.isEmpty())
        <p class="error">Todavía no hay cuentas. Revisa secrets/admin_pin.txt y el registro de arranque.</p>
      @else
        <p id="elige-nombre">Elige tu nombre:</p>
        <ul class="personas" aria-labelledby="elige-nombre">
          @for(Usuario persona : personas)
            <li><button type="submit" name="nombre" value="${persona.nombre()}" class="boton boton-persona">${persona.nombre()}</button></li>
          @endfor
        </ul>
      @endif
      <p class="detalle">Catálogo y ejemplos son compartidos. Configuraciones, ejecuciones, decisiones y posturas son de cada persona.</p>
    </form>
  </section>
`)
