@import pensamiento.web.Pagina
@import pensamiento.web.ControladorMisTrabajos.VistaMisTrabajos
@import pensamiento.web.ControladorMisTrabajos.Trabajo
@import pensamiento.nucleo.Expediente
@param Pagina pagina
@param VistaMisTrabajos v
<%-- "Mis trabajos" (hito 10): lo que guardaste. Solo tú lo ves; tu profe ve solo números en el panel del grupo. --%>
@template.layout(pagina = pagina, contenido = @`
  <section class="mis-trabajos" aria-labelledby="trabajos-titulo">
    <h1 id="trabajos-titulo">Mis trabajos</h1>
    <p class="detalle">Lo que guardaste en las técnicas y en tus expedientes. Solo tú lo ves: tu profe ve en el panel del grupo
      cuántos retos respondieron, nunca lo que escribiste.</p>
    <p><a class="boton boton-secundario" href="/dojo/progreso">Mi progreso en el Dojo</a></p>
    @if(v.trabajos().isEmpty() && v.expedientes().isEmpty())
      <div class="tarjeta vacio"><p>Todavía no guardaste nada. Empieza por el <a href="/reto">reto de hoy</a>.</p></div>
    @endif
    @if(!v.trabajos().isEmpty())
      <h2>Lo más reciente</h2>
      <ul class="lista-trabajos">
        @for(Trabajo t : v.trabajos())
          <li class="tarjeta"><a href="${t.enlace()}">${t.tecnica()}</a> <span class="detalle">${t.fecha()}</span>
            @if(!t.resumen().isBlank())<p class="detalle">${t.resumen()}</p>@endif</li>
        @endfor
      </ul>
    @endif
    @if(!v.expedientes().isEmpty())
      <h2>Mis expedientes</h2>
      <ul class="lista-expedientes">
        @for(Expediente x : v.expedientes())
          <li><a href="/expedientes/${x.id().toString()}">${x.nombre()}</a></li>
        @endfor
      </ul>
    @endif
  </section>
`)
