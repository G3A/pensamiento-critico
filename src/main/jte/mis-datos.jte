@import pensamiento.web.Pagina
@param Pagina pagina
@param String mensaje
@param String error
@template.layout(pagina = pagina, contenido = @`
  <section class="mis-datos" aria-labelledby="mis-datos-titulo">
    <h1 id="mis-datos-titulo">Mis datos</h1>
    <p class="detalle">Tus configuraciones, expedientes y ejecuciones son solo tuyos. El catálogo y los ejemplos son de todos.</p>
    @if(!mensaje.isEmpty())
      <p class="tarjeta" role="status"><span class="chip chip-ok">listo</span> ${mensaje}</p>
    @endif
    @if(!error.isEmpty())
      <p class="error" role="alert" id="importar-error"><span class="chip chip-error">error</span> ${error}</p>
    @endif
    <div class="inicio-columnas">
      <section class="tarjeta" aria-labelledby="exportar-titulo">
        <h2 id="exportar-titulo">Exportar</h2>
        <p>Un archivo JSON con todo lo tuyo. Sirve como respaldo personal o para llevarlo a otra instalación.</p>
        <p><a class="boton" href="/mis-datos/exportar" download>Exportar mis datos (JSON)</a></p>
      </section>
      <section class="tarjeta" aria-labelledby="importar-titulo">
        <h2 id="importar-titulo">Importar</h2>
        <form method="post" action="/mis-datos/importar" enctype="multipart/form-data">
          <input type="hidden" name="_csrf" value="${pagina.csrf()}">
          <label for="importar-archivo">Archivo .json exportado de esta aplicación</label>
          <input type="file" id="importar-archivo" name="archivo" accept="application/json,.json" required
                 aria-describedby="importar-ayuda${error.isEmpty() ? "" : " importar-error"}">
          <p class="ayuda" id="importar-ayuda">Lo que ya tienes con el mismo identificador se actualiza y lo nuevo se agrega. Nada se borra. Puedes importar el mismo archivo dos veces sin duplicar.</p>
          <button type="submit" class="boton">Importar</button>
        </form>
      </section>
    </div>
    <p class="detalle">El respaldo de todo el servidor (con la biblioteca) es <code>docker compose --profile backup run --rm backup</code>.</p>
  </section>
`)
