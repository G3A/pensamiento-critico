@import pensamiento.web.Pagina
@param Pagina pagina
@param gg.jte.Content contenido
<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${pagina.titulo()} · Taller de Pensamiento Crítico</title>
  <meta name="htmx-config" content='{"allowEval":false,"allowScriptTags":false,"includeIndicatorStyles":false,"historyCacheSize":0,"responseHandling":[{"code":"204","swap":false},{"code":"[23]..","swap":true},{"code":"401","swap":true,"error":false},{"code":"422","swap":true,"error":false},{"code":"[45]..","swap":false,"error":true},{"code":"...","swap":true}]}'>
  <meta name="_csrf" content="${pagina.csrf()}">
  <link rel="stylesheet" href="/app.css">
  <link rel="icon" href="/favicon.svg" type="image/svg+xml">
</head>
<body hx-headers='{"X-CSRF-TOKEN": "${pagina.csrf()}"}' x-data="menu">
<a class="saltar" href="#principal">Ir al contenido</a>
<header class="cabecera">
  <div class="marca">
    <button type="button" class="solo-angosto boton-menu" x-on:click="alternar" aria-controls="menu" aria-expanded="false" x-bind:aria-expanded="abiertoTexto">Menú</button>
    <a href="/" class="titulo-app">Taller de Pensamiento Crítico</a>
  </div>
  <div class="estado-ia" role="status">
    @if(pagina.modoPlantillas())
      <span class="chip chip-aviso">sin IA local</span>
      <span class="detalle">Ollama no está disponible: todas las técnicas funcionan en modo plantillas.</span>
    @else
      <span class="chip chip-ok">ollama</span>
      <span class="detalle">qwen3:4b · bge-m3</span>
    @endif
  </div>
  @if(pagina.usuario().isPresent())
    <form method="post" action="/salir" class="cambiar-persona">
      <input type="hidden" name="_csrf" value="${pagina.csrf()}">
      <span class="quien">${pagina.usuario().get().nombre()}</span>
      <button type="submit" class="boton boton-secundario">Cambiar de persona</button>
    </form>
  @endif
</header>
<div class="cuerpo">
  @if(pagina.usuario().isPresent())
    <nav id="menu" class="menu" aria-label="Módulos" x-bind:class="claseMenu">
      <ul>
        <li><a href="/" class="${pagina.titulo().equals("Inicio") ? "activo" : ""}">Inicio</a></li>
        <li><a href="/catalogo" class="${pagina.titulo().equals("Catálogo") ? "activo" : ""}">Catálogo</a></li>
        <li><a href="/expedientes" class="${pagina.titulo().equals("Expedientes") ? "activo" : ""}">Expedientes</a></li>
        <li><a href="/mis-datos" class="${pagina.titulo().equals("Mis datos") ? "activo" : ""}">Mis datos</a></li>
        <li><span class="pendiente-menu">Consejero <small>hito 5</small></span></li>
        <li><span class="pendiente-menu">Taller <small>hito 2</small></span></li>
        <li><span class="pendiente-menu">Verificación <small>hito 6</small></span></li>
        <li><span class="pendiente-menu">Diario <small>hito 4</small></span></li>
        <li><span class="pendiente-menu">Dojo <small>hito 7</small></span></li>
        <li><span class="pendiente-menu">Cambios de opinión <small>hito 7</small></span></li>
        @if(pagina.esAdministrador())
          <li><a href="/usuarios" class="${pagina.titulo().equals("Usuarios") ? "activo" : ""}">Usuarios</a></li>
        @endif
      </ul>
    </nav>
  @endif
  <main id="principal" class="principal" tabindex="-1">
    ${contenido}
  </main>
</div>
<div id="bloqueo" aria-live="polite"></div>
<script src="/htmx.min.js"></script>
<script src="/ext/sse.js"></script>
<script src="/app.js"></script>
<script src="/alpine-csp.min.js" defer></script>
</body>
</html>
