@import pensamiento.web.Pagina
@import pensamiento.web.seguridad.ControladorLlave.VistaLlave
@import pensamiento.nucleo.LlaveDeAcceso
@param Pagina pagina
@param VistaLlave v
<%-- Llave de acceso (hito 9): registrarla, confirmarla o ver las propias. El JavaScript es /llave.js (sin script en línea). --%>
@template.layout(pagina = pagina, contenido = @`
  <section class="entrar-pagina">
    <div class="tarjeta formulario-entrar" data-llave data-base="${v.base()}" data-etapa="${v.etapa()}">
      @if(v.etapa().equals("registrar"))
        <h1>Registra tu llave de acceso</h1>
        <p>Quien administra confirma su entrada con algo que tiene: una <strong>llave de acceso</strong> guardada en este
          celular o portátil, que se abre con su huella, su cara o el bloqueo de pantalla. No hay que instalar nada.</p>
        <p class="detalle">Hazlo en un equipo que solo uses tú. Al terminar verás 10 códigos de recuperación para el día que
          pierdas el equipo: guárdalos en papel.</p>
        <button type="button" class="boton boton-grande" data-llave-registrar>Registrar mi llave</button>
      @elseif(v.etapa().equals("confirmar"))
        <h1>Confirma con tu llave de acceso</h1>
        <p>Tu contraseña está bien. Ahora confirma con la llave de acceso de tu equipo.</p>
        <button type="button" class="boton boton-grande" data-llave-confirmar>Confirmar con mi llave</button>
        <details class="plegable">
          <summary>No tengo mi llave: usar un código de recuperación</summary>
          <form method="post" action="${v.base()}/recuperacion">
            <input type="hidden" name="_csrf" value="${pagina.csrf()}">
            <label for="codigo-recuperacion">Código de recuperación</label>
            <input id="codigo-recuperacion" name="codigo" type="text" autocomplete="one-time-code" autocapitalize="characters"
                   spellcheck="false" maxlength="14" required placeholder="ABCDE-FGHJK">
            <button type="submit" class="boton boton-secundario">Entrar con el código</button>
          </form>
        </details>
      @else
        <h1>Tus llaves de acceso</h1>
        <ul class="llaves">
          @for(LlaveDeAcceso l : v.llaves())
            <li>${l.etiqueta()} <span class="detalle">· registrada el ${v.fecha(l.creada())}, usada por última vez el ${v.fecha(l.usada())}</span></li>
          @endfor
        </ul>
        <button type="button" class="boton boton-secundario" data-llave-registrar>Registrar otra llave</button>
        <p><a href="${v.inicio()}">Volver</a></p>
      @endif
      @if(v.aviso().isPresent())
        <p class="aviso" role="status">${v.aviso().get()}</p>
      @endif
      <p class="error" role="alert" data-llave-estado hidden="${v.error().isEmpty()}">@if(v.error().isPresent())${v.error().get()}@endif</p>
      <noscript><p class="error">La llave de acceso necesita JavaScript activado en el navegador.</p></noscript>
    </div>
  </section>
  <script src="/llave.js" defer></script>
`)
