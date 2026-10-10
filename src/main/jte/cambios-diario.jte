@import pensamiento.web.Pagina
@import pensamiento.web.cambios.ControladorCambios
@param Pagina pagina
@param ControladorCambios.VistaDiario v
@template.layout(pagina = pagina, contenido = @`
  <section class="cambios" aria-labelledby="diario-razonamiento-titulo">
    <h1 id="diario-razonamiento-titulo">Diario de razonamiento</h1>
    <nav class="temas-dojo" aria-label="Registro y diario">
      <ul>
        <li><a href="/cambios-de-opinion">Cambios de opinión</a></li>
        <li><a href="/cambios-de-opinion/diario" aria-current="page" class="activo">Diario de razonamiento</a></li>
      </ul>
    </nav>
    <p class="detalle">T45 · Diario de razonamiento: tus sesiones, mapas y decisiones de las últimas ${v.semanas()} semanas, por semana. Elige
      qué familias entran y cuántas semanas mira en <a href="/tecnicas/T45?pestana=usar">la configuración de T45</a>.</p>
    @if(v.diario().isPresent())
      @template.tag.v.v11(v = v.diario().get())
    @else
      <p class="detalle" role="status">Todavía no hay ejecuciones en estas semanas: lo que guardes en cualquier técnica aparece aquí.</p>
    @endif
  </section>
`)
