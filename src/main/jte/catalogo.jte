@import pensamiento.web.Pagina
@import pensamiento.web.ControladorCatalogo
@import pensamiento.nucleo.Familia
@import pensamiento.catalogo.Intencion
@import java.util.List
@param Pagina pagina
@param List<Familia> familias
@param Intencion[] intenciones
@param List<ControladorCatalogo.Tarjeta> tarjetas
@param ControladorCatalogo.Filtros filtros
@param long total
@param ControladorCatalogo.Recomendacion recomendacion = null
@param List<ControladorCatalogo.EjemploEncontrado> encontrados = java.util.List.of()
@template.layout(pagina = pagina, contenido = @`
  <section class="catalogo" aria-labelledby="catalogo-titulo">
    <h1 id="catalogo-titulo">Catálogo · ${total} técnicas en 8 familias</h1>
    @template.fragmentos.rejilla-catalogo(familias = familias, intenciones = intenciones, tarjetas = tarjetas, filtros = filtros, total = total,
        recomendacion = recomendacion, encontrados = encontrados)
  </section>
`)
