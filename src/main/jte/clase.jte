@import pensamiento.web.Pagina
@import pensamiento.web.aula.ControladorClase.VistaClase
@param Pagina pagina
@param VistaClase v
<%-- La clase de un grupo (hito 10, entregable 4): abrirla da un código corto y un QR para proyectar; "Terminar clase" cierra las
     sesiones de todo el grupo. El QR es un SVG armado en el servidor, sin script. --%>
@template.layout(pagina = pagina, contenido = @`
  <section class="clase" aria-labelledby="clase-titulo">
    <p class="migas no-imprimir"><a href="/grupos/${v.grupo().id().toString()}">${v.grupo().nombre()}</a></p>
    <h1 id="clase-titulo">Clase de ${v.grupo().nombre()}</h1>
    @if(v.clase().isEmpty())
      <p class="detalle">Al abrir la clase aparece un código y un QR para proyectar en el tablero. Cada estudiante entra con su
        nombre y su contraseña: el código solo lleva a la entrada del colegio con su grupo a la vista. Mientras la clase está
        abierta, sus sesiones no se cierran a los 10 minutos.</p>
      <form class="formulario tarjeta" method="post" action="/grupos/${v.grupo().id().toString()}/clase">
        <input type="hidden" name="_csrf" value="${pagina.csrf()}">
        <label for="minutos">Duración</label>
        <select id="minutos" name="minutos">
          <option value="50" selected>50 minutos</option>
          <option value="60">60 minutos</option>
          <option value="45">45 minutos</option>
          <option value="30">30 minutos</option>
        </select>
        <button type="submit" class="boton boton-grande">Abrir clase</button>
      </form>
    @else
      <p class="aviso" role="status">Clase abierta hasta las ${v.cierra()}.</p>
      @if(v.codigo().isPresent())
        <div class="proyectar">
          <div class="qr-marco">$unsafe{v.qr().get()}</div>
          <div class="proyectar-texto">
            <p>Escanea el QR o entra a</p>
            <p class="direccion-clase" translate="no">${v.direccion().get()}</p>
            <p>Código de la clase</p>
            <p class="codigo-grande codigo-clase" translate="no">${v.codigo().get()}</p>
          </div>
        </div>
      @else
        <p class="detalle">El código de esta clase solo se ve en la sesión donde se abrió. Si necesitas verlo otra vez, ábrela de
          nuevo: sale un código nuevo y la clase sigue para quienes ya entraron.</p>
        <form method="post" action="/grupos/${v.grupo().id().toString()}/clase">
          <input type="hidden" name="_csrf" value="${pagina.csrf()}">
          <button type="submit" class="boton boton-secundario">Abrir de nuevo con otro código</button>
        </form>
      @endif
      <form class="no-imprimir terminar-clase" method="post" action="/grupos/${v.grupo().id().toString()}/clase/terminar">
        <input type="hidden" name="_csrf" value="${pagina.csrf()}">
        <button type="submit" class="boton boton-peligro">Terminar clase</button>
        <p class="ayuda">Cierra la clase y las sesiones abiertas de todo el grupo.</p>
      </form>
    @endif
  </section>
`)
