@import pensamiento.web.Pagina
@import pensamiento.nucleo.Familia
@param Pagina pagina
@param java.util.List<Familia> familias
@template.layout(pagina = pagina, contenido = @`
  <section class="manual-indice" aria-labelledby="manual-titulo">
    <h1 id="manual-titulo">Manual por familia</h1>
    <p class="detalle">Ocho capítulos, uno por familia, generados desde las fichas "Qué es" del catálogo y sus ejemplos. Cada uno se lee
      aquí o se descarga como un solo archivo HTML que se abre sin conexión.</p>
    <ul class="lista-familias">
      @for(Familia f : familias)
        <li data-familia="${f.codigo()}">
          <a href="/manual/${f.codigo()}"><strong>${f.codigo()} · ${f.nombre()}</strong></a>
          · <a href="/manual/${f.codigo()}/descargar" download="manual-${f.codigo()}.html">Descargar el capítulo ${f.codigo()}</a>
        </li>
      @endfor
    </ul>
  </section>
`)
