@import pensamiento.web.Pagina
@import pensamiento.web.verificacion.VistaFicha
@param Pagina pagina
@param VistaFicha f
@template.layout(pagina = pagina, contenido = @`
  <section class="ficha-verificacion" aria-labelledby="ficha-titulo" data-afirmacion="${f.id()}">
    <p class="migas"><a href="/verificar">Verificación</a> · Ficha</p>
    <h1 id="ficha-titulo">Ficha de verificación</h1>
    <p class="detalle">Cuatro pasos y un veredicto. La app no dice si algo es cierto: dice qué tanto lo sostienen las fuentes que registras,
      bajo la regla R03, y recalcula el estándar de prueba de los argumentos donde la afirmación es premisa.</p>
    <div class="ficha-cuerpo">
      @template.fragmentos.verificacion.ficha(f = f)
      <aside class="panel-biblioteca tarjeta" aria-labelledby="panel-biblioteca-titulo">
        <h2 id="panel-biblioteca-titulo">Mi biblioteca</h2>
        <form id="form-busqueda-ficha" class="buscar" role="search" action="/biblioteca/buscar" hx-get="/biblioteca/buscar"
              hx-target="#resultados-busqueda" hx-swap="outerHTML">
          <input type="hidden" name="afirmacion" value="${f.id()}">
          <label for="consulta-ficha">Qué dice mi biblioteca sobre</label>
          <input type="search" id="consulta-ficha" name="q" maxlength="300" value="${f.f().afirmacion().texto()}">
          <label class="casilla"><input type="checkbox" name="palabras" value="true"> Solo por palabras</label>
          <button type="submit" class="boton boton-secundario">Buscar</button>
        </form>
        <div id="resultados-busqueda" aria-live="polite" hx-get="/biblioteca/buscar" hx-include="#form-busqueda-ficha" hx-trigger="load"
             hx-swap="outerHTML"><p class="detalle">Buscando en tu biblioteca…</p></div>
        <p class="detalle"><a href="/biblioteca">Importar documentos a la biblioteca</a></p>
      </aside>
    </div>
  </section>
`)
