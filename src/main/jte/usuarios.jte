@import pensamiento.web.Pagina
@import pensamiento.web.usuarios.ControladorUsuarios.VistaUsuarios
@import pensamiento.web.usuarios.ControladorUsuarios.FilaCuenta
@import pensamiento.nucleo.puertos.RepositorioUsuarios.TipoCodigo
@param Pagina pagina
@param VistaUsuarios v
<%-- /usuarios (P21, hito 9): búsqueda con páginas, tarjetas en angosto, alta con código y confirmación antes de desactivar. --%>
@template.layout(pagina = pagina, contenido = @`
  <section class="usuarios">
    <h1>Usuarios</h1>
    <p class="detalle">Creas cada cuenta con un nombre y la app te da un código de alta para imprimir. La persona lo escribe en
      «Tengo un código» y elige su contraseña: nadie más la conoce. Sin autoregistro.</p>
    @if(v.error().isPresent())
      <p class="error" role="alert">${v.error().get()}</p>
    @endif
    @if(v.aviso().isPresent() && !v.aviso().get().isEmpty())
      <p class="aviso" role="status">${v.aviso().get()}</p>
    @endif
    <details class="tarjeta plegable" open="${v.error().isPresent() || v.total() == 0}">
      <summary>Crear una cuenta</summary>
      <form class="formulario" method="post" action="/usuarios">
        <input type="hidden" name="_csrf" value="${pagina.csrf()}">
        <label for="nombre">Nombre de la persona</label>
        <input id="nombre" name="nombre" type="text" maxlength="60" autocomplete="off" required value="${v.nombreEscrito()}">
        <p class="ayuda">Si hay dos personas con el mismo nombre, agrega un apellido o una inicial.</p>
        <fieldset>
          <legend>Rol</legend>
          <label class="opcion"><input type="radio" name="rol" value="persona" checked> Persona</label>
          <label class="opcion"><input type="radio" name="rol" value="administrador"> Administrador (necesita llave de acceso)</label>
        </fieldset>
        <button type="submit" class="boton">Crear y ver su código</button>
      </form>
    </details>
    <details class="tarjeta plegable">
      <summary>«¿No tienes cuenta?»: a quién pedirla</summary>
      <form class="formulario" method="post" action="/usuarios/quien-da-cuentas">
        <input type="hidden" name="_csrf" value="${pagina.csrf()}">
        <label for="texto-quien">Quien no tiene cuenta lee: «Pídele tu cuenta a …»</label>
        <input id="texto-quien" name="texto" type="text" maxlength="120" required value="${v.quienDaCuentas()}">
        <p class="ayuda">Por ejemplo: «tu profe», «la coordinación académica», «quien instaló la app».</p>
        <button type="submit" class="boton boton-secundario">Guardar</button>
      </form>
    </details>
    @if(!v.enEspera().isEmpty())
      <section class="tarjeta" aria-labelledby="esperas-titulo">
        <h2 id="esperas-titulo">Esperando para volver a intentar</h2>
        <p class="detalle">Después de 5 intentos fallidos con un nombre, la app hace esperar antes del siguiente. Si es quien dice
          ser, puedes dejarla intentar ya.</p>
        <ul class="esperas">
          @for(String clave : v.enEspera())
            <li>
              <span>${clave}</span>
              <form method="post" action="/usuarios/espera">
                <input type="hidden" name="_csrf" value="${pagina.csrf()}">
                <input type="hidden" name="nombre" value="${clave}">
                <button type="submit" class="boton boton-secundario">Dejar intentar ya</button>
              </form>
            </li>
          @endfor
        </ul>
      </section>
    @endif
    <form class="buscar-cuentas" method="get" action="/usuarios" role="search">
      <label for="q">Buscar por nombre</label>
      <div class="campo-con-boton">
        <input id="q" name="q" type="search" value="${v.busqueda()}" autocomplete="off">
        <button type="submit" class="boton boton-secundario">Buscar</button>
      </div>
    </form>
    <p class="detalle" role="status">${v.total()} @if(v.total() == 1)cuenta@else cuentas@endif@if(!v.busqueda().isEmpty()) con «${v.busqueda()}»@endif · página ${v.pagina() + 1} de ${v.paginas()}</p>
    <ul class="cuentas">
      @for(FilaCuenta f : v.filas())
        <li class="cuenta tarjeta">
          <div class="cuenta-datos">
            <strong class="cuenta-nombre">${f.cuenta().nombre()}</strong>
            <span class="detalle">${f.cuenta().rol().enBaseDeDatos()}</span>
            @if(!f.cuenta().activo())
              <span class="chip chip-aviso">desactivada</span>
            @elseif(!f.cuenta().tieneClave())
              <span class="chip">@if(f.codigoPendiente().isPresent())código sin canjear@else sin código vigente@endif</span>
            @elseif(f.codigoPendiente().orElse(null) == TipoCodigo.RESTABLECER)
              <span class="chip">código para restablecer sin canjear</span>
            @else
              <span class="chip chip-ok">activa</span>
            @endif
          </div>
          @if(!f.cuenta().id().equals(v.yo()))
            <div class="cuenta-acciones">
              @if(f.cuenta().activo())
                @if(f.cuenta().tieneClave())
                  <form method="post" action="/usuarios/${f.cuenta().id().toString()}/restablecer">
                    <input type="hidden" name="_csrf" value="${pagina.csrf()}">
                    <button type="submit" class="boton boton-secundario" aria-label="Restablecer la contraseña de ${f.cuenta().nombre()}">Restablecer</button>
                  </form>
                @else
                  <form method="post" action="/usuarios/${f.cuenta().id().toString()}/codigo-de-alta">
                    <input type="hidden" name="_csrf" value="${pagina.csrf()}">
                    <button type="submit" class="boton boton-secundario">Código de alta nuevo</button>
                  </form>
                @endif
                <details class="confirmar">
                  <summary class="boton boton-secundario">Desactivar…</summary>
                  <form method="post" action="/usuarios/${f.cuenta().id().toString()}/desactivar">
                    <input type="hidden" name="_csrf" value="${pagina.csrf()}">
                    <p>¿Desactivar a ${f.cuenta().nombre()}? No podrá entrar y sus sesiones se cierran. Sus datos quedan.</p>
                    <button type="submit" class="boton boton-peligro">Sí, desactivar</button>
                  </form>
                </details>
              @else
                <form method="post" action="/usuarios/${f.cuenta().id().toString()}/reactivar">
                  <input type="hidden" name="_csrf" value="${pagina.csrf()}">
                  <button type="submit" class="boton boton-secundario">Reactivar</button>
                </form>
              @endif
            </div>
          @endif
        </li>
      @endfor
    </ul>
    @if(v.paginas() > 1)
      <nav class="paginas" aria-label="Páginas de cuentas">
        @if(v.pagina() > 0)
          <a class="boton boton-secundario" href="${v.enlacePagina(v.pagina() - 1)}" rel="prev">Anterior</a>
        @endif
        @if(v.pagina() + 1 < v.paginas())
          <a class="boton boton-secundario" href="${v.enlacePagina(v.pagina() + 1)}" rel="next">Siguiente</a>
        @endif
      </nav>
    @endif
  </section>
`)
