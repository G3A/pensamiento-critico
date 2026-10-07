@import pensamiento.web.Pagina
@import pensamiento.web.tecnicas.ControladorTecnicas.VistaFicha
@param Pagina pagina
@param VistaFicha ficha
@template.layout(pagina = pagina, contenido = @`
  @template.fragmentos.ficha.cuerpo(ficha = ficha)
`)
