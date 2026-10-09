@import pensamiento.web.Pagina
@import pensamiento.web.biblioteca.VistaBiblioteca
@param Pagina pagina
@param VistaBiblioteca v
@template.layout(pagina = pagina, contenido = @`
  <section class="biblioteca" aria-labelledby="biblioteca-titulo">
    <h1 id="biblioteca-titulo">Biblioteca</h1>
    <p class="detalle">Tus documentos alimentan la ficha de verificación: la búsqueda devuelve pasajes literales, nunca un resumen.
      Lo que importas es privado: nadie más lo ve ni lo encuentra hasta que lo compartas con tu institución.</p>
    <form id="form-subida" class="subida tarjeta" method="post" action="/biblioteca" enctype="multipart/form-data"
          hx-post="/biblioteca" hx-encoding="multipart/form-data" hx-target="#lista-documentos" hx-swap="outerHTML" hx-disabled-elt="find button">
      <input type="hidden" name="_csrf" value="${pagina.csrf()}">
      <label for="archivo">Archivo PDF, Markdown, texto o CSV</label>
      <input type="file" id="archivo" name="archivo" required aria-describedby="archivo-ayuda"
             accept=".pdf,.md,.markdown,.txt,.csv,application/pdf,text/plain,text/markdown,text/csv">
      <p id="archivo-ayuda" class="detalle">Arrastra aquí el archivo o elígelo. Máximo 50 MB. El tipo se reconoce por el contenido, no por el
        nombre. La app no hace OCR: un PDF escaneado queda sin texto.</p>
      <p class="progreso-subida"><label for="progreso-subida">Subida</label>
        <progress id="progreso-subida" data-progreso value="0" max="100">0%</progress></p>
      <button type="submit" class="boton">Importar</button>
    </form>
    @template.fragmentos.biblioteca.lista(v = v, mensaje = "", error = false)
    <section class="buscar-biblioteca" aria-labelledby="buscar-titulo">
      <h2 id="buscar-titulo">Buscar en la biblioteca</h2>
      <form class="buscar" role="search" hx-get="/biblioteca/buscar" hx-target="#resultados-busqueda" hx-swap="outerHTML" action="/biblioteca/buscar">
        <label for="consulta">Qué buscas</label>
        <input type="search" id="consulta" name="q" maxlength="300" required>
        <label class="casilla"><input type="checkbox" name="palabras" value="true"> Solo por palabras</label>
        <button type="submit" class="boton boton-secundario">Buscar</button>
      </form>
      <div id="resultados-busqueda" aria-live="polite"></div>
    </section>
  </section>
`)
