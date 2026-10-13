@import pensamiento.web.Pagina
@param Pagina pagina
@param boolean rutaInvalida
<%-- La raíz sin sesión (hito 8): pide la dirección del colegio y no lista instituciones. --%>
@template.layout(pagina = pagina, contenido = @`
  <section class="bloqueo-pagina">
    <form class="tarjeta formulario-bloqueo" method="get" action="/ir">
      <h1>Entra por la dirección de tu colegio</h1>
      <p class="detalle">Cada colegio u organización tiene su propia dirección, por ejemplo <strong>…/colegio-del-norte</strong>.
        Te la da quien administra la app en tu colegio; también puede estar en un código QR en el salón.</p>
      @if(rutaInvalida)
        <p class="error" role="alert">Esa dirección no tiene la forma de una dirección de colegio: usa solo letras sin tildes, números y guiones.</p>
      @endif
      <label for="institucion">La dirección de tu colegio (lo que va después de la barra)</label>
      <input id="institucion" name="institucion" type="text" autocomplete="off" autocapitalize="none" spellcheck="false" required autofocus
             placeholder="colegio-del-norte">
      <button type="submit" class="boton">Ir a la entrada</button>
    </form>
  </section>
`)
