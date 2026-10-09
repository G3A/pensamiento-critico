@import pensamiento.flujos.BuscadorDePasajes
@import pensamiento.nucleo.Pasaje
@param BuscadorDePasajes.Busqueda b
@param String afirmacion
<div id="resultados-busqueda" class="resultados-busqueda" aria-live="polite">
  @if(!b.consulta().isEmpty())
    <p class="detalle modo-busqueda">${b.modo() == Pasaje.Modo.SEMANTICA ? "Búsqueda semántica (bge-m3)" : "Búsqueda por palabras"} · «${b.consulta()}»</p>
  @endif
  @if(b.aviso().isPresent())
    <p class="aviso-ia" role="status"><span class="chip chip-aviso">aviso</span> ${b.aviso().get()}</p>
  @endif
  @if(b.pasajes().isEmpty() && !b.consulta().isEmpty())
    <p class="detalle">Ningún pasaje de tu biblioteca coincide.</p>
  @else
    <ol class="pasajes" aria-label="Pasajes encontrados">
      @for(Pasaje p : b.pasajes())
        <li data-fragmento="${p.fragmentoId().toString()}" data-documento="${p.documentoId().toString()}">
          <p class="origen-pasaje"><strong>${p.documento()}</strong>@if(p.pagina().isPresent()), p. ${p.pagina().get()}@endif
            · <span class="detalle">${p.modo() == Pasaje.Modo.SEMANTICA ? "similitud " + String.format(java.util.Locale.forLanguageTag("es-CO"), "%.2f", p.puntaje()) : "coincide por palabras"}</span></p>
          <blockquote class="pasaje">«${p.texto()}»</blockquote>
          @if(!afirmacion.isEmpty())
            <p class="acciones-pasaje">
              <a class="boton boton-secundario boton-chico" href="/verificar/${afirmacion}/fuentes/nueva?fragmento=${p.fragmentoId().toString()}">Usar como evidencia</a>
              <button type="button" class="boton boton-secundario boton-chico" hx-post="/verificar/${afirmacion}/pasajes/${p.fragmentoId().toString()}/etiqueta"
                      hx-target="#etiqueta-${p.fragmentoId().toString()}" hx-swap="innerHTML" hx-disabled-elt="this">Etiquetar con el modelo</button>
            </p>
            <div id="etiqueta-${p.fragmentoId().toString()}" class="etiqueta-pasaje"></div>
          @endif
        </li>
      @endfor
    </ol>
    @if(!b.pasajes().isEmpty())
      <p class="detalle">Los resultados son pasajes literales. El modelo nunca resume ni inventa: si se lo pides, solo etiqueta si un pasaje apoya
        o contradice la afirmación desde la que buscas, y eso no cuenta hasta que lo adoptas.</p>
    @endif
  @endif
</div>
