@import pensamiento.web.Pagina
@param Pagina pagina
@param gg.jte.Content contenido
<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${pagina.titulo()} · Taller de Pensamiento Crítico</title>
  <meta name="htmx-config" content='{"allowEval":false,"allowScriptTags":false,"includeIndicatorStyles":false,"historyCacheSize":0,"responseHandling":[{"code":"204","swap":false},{"code":"[23]..","swap":true},{"code":"401","swap":true,"error":false},{"code":"422","swap":true,"error":false},{"code":"429","swap":true,"error":false},{"code":"[45]..","swap":false,"error":true},{"code":"...","swap":true}]}'>
  <meta name="_csrf" content="${pagina.csrf()}">
  <link rel="stylesheet" href="/app.css">
  <link rel="icon" href="/favicon.svg" type="image/svg+xml">
</head>
<body hx-headers="${pagina.cabecerasHtmx()}" class="${pagina.conMenu() ? "con-menu" : "sin-menu"}">
<a class="saltar" href="#principal">Ir al contenido</a>
<%-- Hito 9: cabecera de 56 px como máximo; sin sesión, ni modelo ni menú (innegociables 10 y 12). --%>
<header class="cabecera">
  <a href="/" class="titulo-app"><span class="titulo-largo">Taller de Pensamiento Crítico</span><span class="titulo-corto" aria-hidden="true">Taller PC</span></a>
  @if(pagina.conMenu())
    <div class="cabecera-derecha">
      @if(pagina.modoPlantillas())
        <span class="chip solo-ancho" title="Todas las técnicas funcionan igual; solo no aparece &quot;Pedir propuestas al modelo&quot;.">sin asistente de IA</span>
      @endif
      <span class="quien">${pagina.usuario().get().nombre()}</span>
      <form method="post" action="/salir" class="salir">
        <input type="hidden" name="_csrf" value="${pagina.csrf()}">
        <button type="submit" class="boton boton-secundario">Salir</button>
      </form>
    </div>
  @elseif(pagina.usuario().isPresent())
    <form method="post" action="/salir" class="salir">
      <input type="hidden" name="_csrf" value="${pagina.csrf()}">
      <button type="submit" class="boton boton-secundario">Salir</button>
    </form>
  @endif
</header>
<div class="cuerpo">
  @if(pagina.conMenu())
    <%-- Cuatro grupos (innegociable 12). En angosto se abre con "Más" de la barra inferior (#menu, :target, sin JS). --%>
    <nav id="menu" class="menu" aria-label="Módulos">
      <a class="cerrar-menu solo-angosto" href="#">Cerrar</a>
      <h2 class="grupo-menu">Hacer</h2>
      <ul>
        <li><a href="/" class="${pagina.titulo().equals("Inicio") ? "activo" : ""}">Inicio</a></li>
        <li><a href="/catalogo" class="${pagina.titulo().equals("Catálogo") ? "activo" : ""}">Catálogo</a></li>
        <li><a href="/expedientes" class="${pagina.titulo().equals("Expedientes") ? "activo" : ""}">Expedientes</a></li>
        <li><a href="/taller" class="${pagina.titulo().equals("Taller") ? "activo" : ""}">Taller</a></li>
        <li><a href="/verificar" class="${pagina.titulo().equals("Verificación") ? "activo" : ""}">Verificación</a></li>
        <li><a href="/biblioteca" class="${pagina.titulo().equals("Biblioteca") ? "activo" : ""}">Biblioteca</a></li>
      </ul>
      <h2 class="grupo-menu">Pensar con apoyo</h2>
      <ul>
        <li><a href="/consejero" class="${pagina.titulo().equals("Consejero") ? "activo" : ""}">Consejero</a></li>
        <li><a href="/diario" class="${pagina.titulo().equals("Diario") ? "activo" : ""}">Diario</a></li>
      </ul>
      <h2 class="grupo-menu">Practicar y revisar</h2>
      <ul>
        <li><a href="/dojo" class="${pagina.titulo().equals("Dojo") ? "activo" : ""}">Dojo</a></li>
        <li><a href="/cambios-de-opinion" class="${pagina.titulo().equals("Cambios de opinión") ? "activo" : ""}">Cambios de opinión</a></li>
        <li><a href="/manual" class="${pagina.titulo().equals("Manual") ? "activo" : ""}">Manual</a></li>
      </ul>
      @if(pagina.esAdministrador() || pagina.esDocente())
        <h2 class="grupo-menu">Aula</h2>
        <ul>
          <li><a href="/grupos" class="${pagina.titulo().equals("Grupos") || pagina.titulo().equals("Mis grupos") ? "activo" : ""}">@if(pagina.esAdministrador())Grupos@else Mis grupos@endif</a></li>
        </ul>
      @endif
      <h2 class="grupo-menu">Cuenta</h2>
      <ul>
        <li><a href="/cuenta" class="${pagina.titulo().equals("Mi cuenta") ? "activo" : ""}">Mi cuenta</a></li>
        <li><a href="/mis-datos" class="${pagina.titulo().equals("Mis datos") ? "activo" : ""}">Mis datos</a></li>
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
@if(pagina.conMenu())
  <nav class="barra-inferior solo-angosto" aria-label="Accesos rápidos">
    <a href="/" class="${pagina.titulo().equals("Inicio") ? "activo" : ""}">Inicio</a>
    <a href="/catalogo" class="${pagina.titulo().equals("Catálogo") ? "activo" : ""}">Catálogo</a>
    <a href="/dojo" class="${pagina.titulo().equals("Dojo") ? "activo" : ""}">Practicar</a>
    <a href="#menu">Más</a>
  </nav>
@endif
<div id="bloqueo" aria-live="polite"></div>
<script src="/htmx.min.js"></script>
<script src="/ext/sse.js"></script>
<script src="/app.js"></script>
<script src="/alpine-csp.min.js" defer></script>
</body>
</html>
