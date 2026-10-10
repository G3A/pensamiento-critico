@import pensamiento.web.Pagina
@import pensamiento.web.manual.ManualPorFamilia
@param Pagina pagina
@param ManualPorFamilia.Pintado p
@template.layout(pagina = pagina, contenido = @`
  <p class="acciones-reto">
    <a href="/manual" class="boton boton-secundario">Todas las familias</a>
    <a href="/manual/${p.familia().codigo()}/descargar" class="boton" download="manual-${p.familia().codigo()}.html">Descargar este capítulo</a>
  </p>
  $unsafe{p.html()}
`)
