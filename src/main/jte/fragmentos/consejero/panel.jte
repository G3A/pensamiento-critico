@import pensamiento.web.consejero.VistaConsejero
@import pensamiento.web.patrones.V09
@param VistaConsejero.Sesion v
@param boolean oob
<aside id="panel-consejero" class="panel-lateral" aria-label="${v.panel().titulo()}" hx-swap-oob="${oob ? "true" : null}">
  <h2 class="subtitulo-resultado">${v.panel().titulo()}</h2>
  @for(V09.SeccionPanel s : v.panel().secciones())
    @if(s.titulo() != null)
      <h3 class="titulo-panel">${s.titulo()}</h3>
    @endif
    <ul class="items-panel">
      @for(V09.ItemPanel it : s.items())
        <li data-item="${it.clave()}">
          <span class="${it.claseChip()}">${it.chip()}</span> <strong>${it.nombre()}</strong>
          @if(it.texto() != null)
            <span class="texto-parte">${it.texto()}</span>
          @endif
          @if(it.detalle() != null)
            <p class="detalle">${it.detalle()}</p>
          @endif
        </li>
      @endfor
    </ul>
  @endfor
  @if(!v.panel().propuestas().isEmpty())
    <h3 class="titulo-panel">Propuestas del modelo</h3>
    <ul class="items-panel propuestas-elemento">
      @for(VistaConsejero.PropuestaElemento p : v.panel().propuestas())
        <li data-propuesta="${p.turnoId().toString()}">
          @if(p.adoptada())
            <span class="chip chip-ok">adoptada</span>
          @else
            <span class="chip chip-pendiente">propuesta del modelo · sin adoptar · no cuenta</span>
          @endif
          <strong>${p.elemento()}</strong> <span class="texto-parte">«${p.texto()}»</span>
          <p class="detalle">${p.porque()}</p>
          @if(!p.adoptada() && !v.cerrada())
            <button type="button" class="boton boton-secundario boton-chico" hx-post="/consejero/turnos/${p.turnoId().toString()}/elemento?sesion=${v.sesion().id().toString()}"
                    hx-target="#panel-consejero" hx-swap="outerHTML" data-accion="adoptar-elemento">Adoptar</button>
          @endif
        </li>
      @endfor
    </ul>
  @endif
</aside>
