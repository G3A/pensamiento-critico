@import pensamiento.web.formulario.VistaCampo
@import pensamiento.web.formulario.Campo
@param VistaCampo c
@if(c.tipo() == Campo.Tipo.TEXTO)
  @template.tag.campo.texto(c = c)
@elseif(c.tipo() == Campo.Tipo.ENTERO)
  @template.tag.campo.entero(c = c)
@elseif(c.tipo() == Campo.Tipo.BOOLEANO)
  @template.tag.campo.booleano(c = c)
@elseif(c.tipo() == Campo.Tipo.ENUMERACION)
  @template.tag.campo.enumeracion(c = c)
@elseif(c.tipo() == Campo.Tipo.CONJUNTO)
  @template.tag.campo.conjunto(c = c)
@elseif(c.tipo() == Campo.Tipo.LISTA_ORDENADA)
  @template.tag.campo.lista_ordenada(c = c)
@elseif(c.tipo() == Campo.Tipo.FECHA)
  @template.tag.campo.fecha(c = c)
@elseif(c.tipo() == Campo.Tipo.FILAS)
  @template.tag.campo.filas(c = c)
@endif
