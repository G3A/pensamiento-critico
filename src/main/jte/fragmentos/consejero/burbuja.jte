@import pensamiento.web.consejero.VistaConsejero
@param VistaConsejero.Burbuja b
@if(b.redactando())
  <li id="${b.id()}" class="burbuja burbuja-${b.rol()}" data-turno="${b.numero()}" hx-ext="sse" sse-connect="/consejero/turnos/${b.turnoId().toString()}/flujo"
      sse-close="fin" aria-busy="true">
    <p class="autor"><strong>${b.autor()}</strong> <span class="chip chip-pendiente">redactando</span>
      <span class="tiempo" sse-swap="tiempo">0 s</span></p>
    <p class="estado-espera" role="status">Esperando al modelo: el texto que aparece es provisional y la versión validada lo reemplaza.
      @if(b.cola() > 0)
        <span class="cola-ia">En cola: ${b.cola()} ${b.cola() == 1 ? "pedido" : "pedidos"} antes que el tuyo.</span>
      @endif
    </p>
    <p class="texto-burbuja provisional" sse-swap="token" hx-swap="beforeend" aria-live="off"></p>
    <button type="button" class="boton boton-secundario boton-chico" hx-post="/consejero/turnos/${b.turnoId().toString()}/cancelar?sesion=${b.sesionId().toString()}"
            hx-target="#${b.id()}" hx-swap="outerHTML" data-accion="cancelar">Cancelar</button>
    <div class="fin-espera" sse-swap="fin" hx-target="#${b.id()}" hx-swap="outerHTML"></div>
  </li>
@else
  <li id="${b.id()}" class="burbuja burbuja-${b.rol()}" data-turno="${b.numero()}">
    <p class="autor"><strong>${b.autor()}</strong>
      @for(String chip : b.chips())
        <span class="chip">${chip}</span>
      @endfor
    </p>
    <p class="texto-burbuja">${b.texto()}</p>
    @if(b.detalle() != null)
      <p class="detalle">${b.detalle()}</p>
    @endif
  </li>
@endif
