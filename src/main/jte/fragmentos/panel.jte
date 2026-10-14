@import pensamiento.web.aula.ControladorPanel
@import pensamiento.web.aula.ControladorPanel.VistaPanel
@import pensamiento.web.aula.ControladorPanel.FilaConcepto
@import pensamiento.nucleo.PanelGrupo
@param VistaPanel v
<%-- Panel del grupo (hito 10, entregable 7): solo números y los nombres de quien lleva días sin practicar; nunca una respuesta.
     Se actualiza solo cada 30 segundos (htmx, sin script propio). --%>
<div id="panel" class="panel-grupo" hx-get="/grupos/${v.panel().grupo().id().toString()}/panel" hx-trigger="every 30s" hx-swap="outerHTML"
     hx-select="#panel" aria-live="polite">
  <ul class="cifras">
    <li class="tarjeta cifra"><span class="cifra-numero">${v.a().entraronHoy()} de ${v.a().miembros()}</span><span class="cifra-texto">entraron hoy</span></li>
    @if(v.panel().clase().isPresent())
      <li class="tarjeta cifra"><span class="cifra-numero">${v.a().entraronEnClase()}</span><span class="cifra-texto">entraron en esta clase</span></li>
    @endif
    <li class="tarjeta cifra"><span class="cifra-numero">${v.a().retosHoy()}</span><span class="cifra-texto">retos respondidos hoy, por ${v.a().respondieronHoy()}@if(v.a().respondieronHoy() == 1) estudiante@else estudiantes@endif</span></li>
    @if(!v.tema().isEmpty())
      <li class="tarjeta cifra"><span class="cifra-numero">${v.a().retosDelTema()}</span><span class="cifra-texto">retos del tema en total</span></li>
    @endif
  </ul>
  @if(v.tema().isEmpty())
    <p class="detalle">Sin tema asignado: asígnale uno al grupo para ver aciertos por concepto y niveles.</p>
  @else
    <section class="tarjeta" aria-labelledby="aciertos-titulo">
      <h2 id="aciertos-titulo">Aciertos por concepto · ${v.tema()}</h2>
      <p class="detalle">Retos de identificar, analizar y evaluar, calificados por reglas.</p>
      @if(v.conceptos().isEmpty())
        <p class="detalle">Todavía nadie respondió retos de este tema.</p>
      @else
        <table class="tabla-panel">
          <thead><tr><th scope="col">Concepto</th><th scope="col">Aciertos</th><th scope="col">Estudiantes</th></tr></thead>
          <tbody>
            @for(FilaConcepto c : v.conceptos())
              <tr><td>${c.nombre()}</td><td>${c.aciertos()} de ${c.intentos()} (${c.porcentaje()} %)</td><td>${c.personas()}</td></tr>
            @endfor
          </tbody>
        </table>
      @endif
    </section>
    <section class="tarjeta" aria-labelledby="crear-titulo">
      <h2 id="crear-titulo">Retos de crear · práctica, no nota</h2>
      <p class="detalle">Reescribir sin el error se revisa con una rúbrica de chequeos: sirve para practicar, no para calificar.</p>
      @if(v.crear().isEmpty())
        <p class="detalle">Todavía nadie llegó a los retos de crear de este tema.</p>
      @else
        <table class="tabla-panel">
          <thead><tr><th scope="col">Concepto</th><th scope="col">Cumplieron la rúbrica</th><th scope="col">Estudiantes</th></tr></thead>
          <tbody>
            @for(FilaConcepto c : v.crear())
              <tr><td>${c.nombre()}</td><td>${c.aciertos()} de ${c.intentos()}</td><td>${c.personas()}</td></tr>
            @endfor
          </tbody>
        </table>
      @endif
    </section>
    <section class="tarjeta" aria-labelledby="bloom-titulo">
      <h2 id="bloom-titulo">Nivel alcanzado (T48 · Taxonomía de Bloom)</h2>
      <ul class="niveles-panel">
        @for(PanelGrupo.Nivel n : v.bloom())
          <li><span class="chip">${n.nivel()}</span> ${n.personas()}@if(n.personas() == 1) estudiante@else estudiantes@endif</li>
        @endfor
      </ul>
    </section>
  @endif
  <section class="tarjeta" aria-labelledby="sin-practicar-titulo">
    <h2 id="sin-practicar-titulo">Llevan 3 días o más sin practicar</h2>
    @if(v.a().sinPracticar().isEmpty())
      <p class="detalle">Nadie: todo el grupo practicó en los últimos 3 días.</p>
    @else
      <ul class="sin-practicar">
        @for(PanelGrupo.SinPracticar s : v.a().sinPracticar())
          <li>${s.nombre()} <span class="detalle">${ControladorPanel.dias(s.dias())}</span></li>
        @endfor
      </ul>
    @endif
  </section>
</div>
