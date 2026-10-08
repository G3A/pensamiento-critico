@import pensamiento.web.diario.ControladorDiario
@param ControladorDiario.VistaAsistente a
!{String base = "/diario/decisiones/" + a.expediente().id();}
<nav id="pasos-decision" class="pasos-decision" aria-label="Pasos de la decisión"
     hx-get="${base}/pasos?paso=${a.paso().numero()}&tecnica=${a.tecnica().id().valor()}" hx-trigger="ejecucion-guardada from:body" hx-swap="outerHTML">
  <ol class="pasos">
    @for(ControladorDiario.PasoVista p : a.pasos())
      <li class="${p.actual() ? "paso actual" : "paso"}" aria-current="${p.actual() ? "step" : null}">
        @if(p.habilitado())
          <a href="${base}?paso=${p.numero()}">${p.numero()} · ${p.nombre()}</a>
        @else
          <span class="inhabilitado">${p.numero()} · ${p.nombre()}</span> <span class="chip chip-pendiente">primero el paso 0</span>
        @endif
      </li>
    @endfor
  </ol>
  <ul class="tecnicas-paso" aria-label="Técnicas del paso ${a.paso().numero()}">
    @for(ControladorDiario.TecnicaVista t : a.paso().tecnicas())
      <li class="${t.actual() ? "actual" : ""}">
        <a href="${t.url()}" aria-current="${t.actual() ? "page" : null}" data-tecnica="${t.tecnica().id().valor()}">${t.tecnica().cita()}</a>
        @if(t.hecha())<span class="chip chip-ok">guardada</span>@endif
        @if(t.obligatoria() && !t.hecha())<span class="chip chip-aviso">obligatoria</span>@endif
        @if(!t.habilitada())<span class="chip chip-pendiente">bloqueada</span>@endif
      </li>
    @endfor
  </ul>
  @if(a.registrada())
    <p role="status"><span class="chip chip-ok">registrada</span> La decisión ya está en el Diario: aparecerá para revisar en su fecha.
      <a href="/diario">Ir al Diario</a></p>
  @elseif(a.problemaDefinido())
    <p role="status"><span class="chip chip-ok">paso 0 listo</span> El problema está definido: puedes seguir con los pasos 1 a 4.</p>
  @endif
</nav>
