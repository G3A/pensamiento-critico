@import pensamiento.web.Pagina
@import pensamiento.web.taller.VistaArgumento
@import pensamiento.nucleo.Argumento
@param Pagina pagina
@param VistaArgumento a
@template.layout(pagina = pagina, contenido = @`
  <section class="argumento" id="argumento-${a.argumento().id().toString()}" aria-labelledby="argumento-titulo">
    <h1 id="argumento-titulo">Argumento ${a.sentido()}</h1>
    <p><strong>Conclusión:</strong> ${a.texto(a.argumento().conclusionId())}</p>
    <ol class="premisas-argumento">
      @for(Argumento.Premisa p : a.argumento().premisas())
        <li>${a.texto(p.afirmacionId())}@if(p.asumible()) <span class="chip chip-pendiente">supuesto</span>@endif</li>
      @endfor
    </ol>
    <p class="detalle">Peso ${a.argumento().peso()} · estándar ${a.guardado().argumento().estandar().nombre()}.</p>
    @if(a.guardado().argumento().textoArgdown().isPresent())
      <details><summary>Texto Argdown del mapa</summary><pre class="argdown">${a.guardado().argumento().textoArgdown().get()}</pre></details>
    @endif
    <p><a href="/ejecuciones/${a.guardado().ejecucionId().toString()}">Abrir la ejecución que lo produjo</a></p>
  </section>
`)
