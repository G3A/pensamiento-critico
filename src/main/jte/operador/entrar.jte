@import pensamiento.web.Pagina
@param Pagina pagina
@param boolean error
<%-- Entrada del operador del servicio (hito 8): fuera de toda institución, con la clave del secreto del despliegue. --%>
@template.layout(pagina = pagina, contenido = @`
  <section class="bloqueo-pagina">
    <form class="tarjeta formulario-bloqueo" method="post" action="/operador/sesion">
      <input type="hidden" name="_csrf" value="${pagina.csrf()}">
      <input type="hidden" name="nombre" value="operador">
      <h1>Operador del servicio</h1>
      <p class="detalle">Crea instituciones e invita a su primer administrador. No ve los datos de ninguna institución.</p>
      @if(error)
        <p class="error" role="alert">La clave no coincide.</p>
      @endif
      <label for="clave">Clave del operador</label>
      <input id="clave" name="clave" type="password" autocomplete="current-password" minlength="12" required autofocus>
      <button type="submit" class="boton">Entrar</button>
    </form>
  </section>
`)
