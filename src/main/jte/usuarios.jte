@import pensamiento.web.Pagina
@import pensamiento.nucleo.Usuario
@import java.util.List
@param Pagina pagina
@param List<Usuario> usuarios
@param String error
@template.layout(pagina = pagina, contenido = @`
  <section class="usuarios">
    <h1>Usuarios</h1>
    <p class="detalle">Una cuenta por persona, siempre con PIN. No hay perfil invitado ni autoregistro: tú creas y desactivas.</p>
    @if(error != null)
      <p class="error" role="alert">${error}</p>
    @endif
    <form class="tarjeta formulario" method="post" action="/usuarios">
      <input type="hidden" name="_csrf" value="${pagina.csrf()}">
      <h2>Nueva persona</h2>
      <label for="nombre">Nombre</label>
      <input id="nombre" name="nombre" type="text" maxlength="60" required>
      <label for="pin">PIN (4 a 8 dígitos)</label>
      <input id="pin" name="pin" type="password" inputmode="numeric" pattern="[0-9]{4,8}" required>
      <button type="submit" class="boton">Crear</button>
    </form>
    <table class="tabla">
      <caption>Cuentas de esta instalación</caption>
      <thead><tr><th scope="col">Nombre</th><th scope="col">Rol</th><th scope="col">Estado</th><th scope="col">Acción</th></tr></thead>
      <tbody>
        @for(Usuario u : usuarios)
          <tr>
            <td>${u.nombre()}</td>
            <td>${u.rol().name().toLowerCase()}</td>
            <td>@if(u.activo())<span class="chip chip-ok">activa</span>@else<span class="chip chip-aviso">desactivada</span>@endif</td>
            <td>
              @if(u.activo() && !u.esAdministrador())
                <form method="post" action="/usuarios/${u.id().toString()}/desactivar">
                  <input type="hidden" name="_csrf" value="${pagina.csrf()}">
                  <button type="submit" class="boton boton-secundario">Desactivar</button>
                </form>
              @endif
            </td>
          </tr>
        @endfor
      </tbody>
    </table>
  </section>
`)
