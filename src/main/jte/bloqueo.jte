@import pensamiento.web.Pagina
@import pensamiento.web.seguridad.RespuestasSesion.ModeloEntrada
@param Pagina pagina
@param ModeloEntrada entrada
<%-- La entrada de cada institución (/<ruta>), hito 9: nombre, contraseña y "Entrar"; sin lista de nombres. --%>
@template.layout(pagina = pagina, contenido = @`
  <section class="entrar-pagina">
    @template.tag.entrar(m = entrada)
  </section>
`)
