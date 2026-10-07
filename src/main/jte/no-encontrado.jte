@import pensamiento.web.Pagina
@param Pagina pagina
@param String mensaje
@template.layout(pagina = pagina, contenido = @`
  <section class="no-encontrado">
    <h1>No encontrado</h1>
    <p>${mensaje}</p>
    <p><a class="boton" href="/">Volver al inicio</a></p>
  </section>
`)
