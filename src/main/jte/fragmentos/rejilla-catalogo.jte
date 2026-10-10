@import pensamiento.web.ControladorCatalogo
@import pensamiento.nucleo.Familia
@import pensamiento.nucleo.Tecnica
@import pensamiento.catalogo.Intencion
@import java.util.List
@param List<Familia> familias
@param Intencion[] intenciones
@param List<ControladorCatalogo.Tarjeta> tarjetas
@param ControladorCatalogo.Filtros filtros
@param long total
<div id="catalogo-cuerpo" class="catalogo-cuerpo">
  <nav class="familias" aria-label="Familias">
    <ul>
      <li><a href="/catalogo" hx-get="/catalogo" hx-target="#catalogo-cuerpo" hx-swap="outerHTML" hx-push-url="true"
             aria-current="${filtros.familia().isEmpty() ? "true" : null}" class="${filtros.familia().isEmpty() ? "activo" : ""}">Todas <span class="conteo">${total}</span></a></li>
      @for(Familia familia : familias)
        <li><a href="${filtros.conFamilia(familia.codigo())}" hx-get="${filtros.conFamilia(familia.codigo())}" hx-target="#catalogo-cuerpo"
               hx-swap="outerHTML" hx-push-url="true" aria-current="${filtros.familia().equals(familia.codigo()) ? "true" : null}"
               class="${filtros.familia().equals(familia.codigo()) ? "activo" : ""}">${familia.codigo()} ${familia.nombre()}</a></li>
      @endfor
    </ul>
  </nav>
  <div class="catalogo-principal">
    <form class="filtros" action="/catalogo" method="get" role="search" hx-get="/catalogo" hx-target="#resultados-catalogo" hx-select="#resultados-catalogo" hx-swap="outerHTML"
          hx-push-url="true" hx-trigger="submit, input changed delay:400ms from:#buscar-tecnica, change from:#filtro-intencion">
      <input type="hidden" name="familia" value="${filtros.familia()}">
      <label for="buscar-tecnica">Buscar por nombre</label>
      <input type="search" id="buscar-tecnica" name="q" value="${filtros.q()}" placeholder="por ejemplo: hipótesis">
      <label for="filtro-intencion">Qué quieres hacer</label>
      <select id="filtro-intencion" name="intencion">
        <option value="">Cualquier intención</option>
        @for(Intencion i : intenciones)
          <option value="${i.clave()}" selected="${i.clave().equals(filtros.intencion())}">${i.titulo()}</option>
        @endfor
      </select>
      <button type="submit" class="boton boton-secundario">Filtrar</button>
    </form>
    <div id="resultados-catalogo">
    <div id="rejilla" class="rejilla-encabezado">
      <p class="detalle" role="status">${tarjetas.size()} ${tarjetas.size() == 1 ? "técnica" : "técnicas"} en pantalla
        @if(!filtros.tituloIntencion().isEmpty()) · intención: <strong>${filtros.tituloIntencion()}</strong>@endif
        @if(!filtros.q().isEmpty()) · nombre: <strong>${filtros.q()}</strong>@endif
        @if(filtros.alguno()) · <a href="/catalogo">quitar filtros</a>@endif
      </p>
    </div>
    @if("practicar".equals(filtros.intencion()))
      <p class="aviso-lista aviso-nota" data-flujo="dojo">Para practicar todos los días, el <a href="/dojo">Dojo de razonamiento</a> te da retos
        cortos de falacias, sesgos, datos y fuentes, y programa cada repaso.</p>
    @endif
    @if(tarjetas.isEmpty())
      <div class="tarjeta vacio"><p>Ninguna técnica coincide. Prueba con otra palabra o quita los filtros.</p></div>
    @endif
    <ul class="rejilla" aria-label="Técnicas">
      @for(ControladorCatalogo.Tarjeta tarjeta : tarjetas)
        !{Tecnica t = tarjeta.tecnica();}
        <li class="tarjeta tecnica" id="tecnica-${t.id().valor()}" data-familia="${t.familia()}">
          <h2 class="nombre-llano"><a href="/tecnicas/${t.id().valor()}">${t.nombreLlano()}</a></h2>
          <p class="cita">${t.cita()}</p>
          <p class="chips">
            <span class="chip">${t.familia()}</span>
            <span class="chip">${t.modalidad().name().toLowerCase()}</span>
            @if(t.requiereIa() == Tecnica.RequiereIa.NO)
              <span class="chip chip-ok">sin IA</span>
            @elseif(t.requiereIa() == Tecnica.RequiereIa.OPCIONAL)
              <span class="chip">Ollama opcional</span>
            @else
              <span class="chip chip-aviso">necesita Ollama</span>
            @endif
            @if(tarjeta.disponible())
              <span class="chip chip-ok">disponible · ${tarjeta.ejemplos()} ejemplos</span>
            @else
              <span class="chip chip-pendiente">llega en el hito ${tarjeta.hito()}</span>
            @endif
          </p>
          <p class="usala"><strong>Úsala cuando</strong> ${t.usalaCuando()}</p>
        </li>
      @endfor
    </ul>
    </div>
  </div>
</div>
