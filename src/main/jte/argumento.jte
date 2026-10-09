@import pensamiento.web.Pagina
@import pensamiento.web.taller.VistaArgumento
@import pensamiento.nucleo.Argumento
@import pensamiento.nucleo.EstandarPrueba
@import pensamiento.web.verificacion.VistaFicha
@param Pagina pagina
@param VistaArgumento a
@template.layout(pagina = pagina, contenido = @`
  <section class="argumento" id="argumento-${a.argumento().id().toString()}" aria-labelledby="argumento-titulo">
    <h1 id="argumento-titulo">Argumento ${a.sentido()}</h1>
    <p><strong>Conclusión:</strong> ${a.texto(a.argumento().conclusionId())}</p>
    <ol class="premisas-argumento">
      @for(Argumento.Premisa p : a.argumento().premisas())
        <li data-premisa="${p.afirmacionId().toString()}">${a.texto(p.afirmacionId())}@if(p.asumible()) <span class="chip chip-pendiente">supuesto</span>@endif
          <span class="${VistaFicha.claseEstado(a.estado(p.afirmacionId()))}">${VistaFicha.estado(a.estado(p.afirmacionId()))}</span>
          <a class="boton boton-secundario boton-chico" href="/verificar/${p.afirmacionId().toString()}">Verificar</a></li>
      @endfor
    </ol>
    <p class="detalle">Peso ${a.argumento().peso()} · estándar ${a.guardado().argumento().estandar().nombre()}.</p>
    @if(a.r04().isPresent())
      <section class="r04 tarjeta" aria-labelledby="r04-argumento-titulo">
        <h2 id="r04-argumento-titulo">Con lo que verificaste (R04)</h2>
        <p><span class="${a.r04().get().aceptable().get(a.r04().get().estandar()) ? "chip chip-ok" : "chip chip-aviso"}" data-aceptable="${String.valueOf(a.r04().get().aceptable().get(a.r04().get().estandar()))}">${a.r04().get().aceptable().get(a.r04().get().estandar()) ? "se cumple" : "no se cumple"}</span>
          ${a.r04().get().frase()}</p>
        <ul class="estandares-argumento">
          @for(EstandarPrueba e : EstandarPrueba.values())
            <li data-estandar="${e.name().toLowerCase()}">${e.nombre()}: ${VistaFicha.si(a.r04().get().aceptable().get(e))}</li>
          @endfor
        </ul>
        <p class="detalle">Una premisa verificada en su ficha cuenta como aceptada; una disputada queda cuestionada; un supuesto sin objeción sigue aceptado.</p>
      </section>
    @endif
    @if(a.guardado().argumento().textoArgdown().isPresent())
      <details><summary>Texto Argdown del mapa</summary><pre class="argdown">${a.guardado().argumento().textoArgdown().get()}</pre></details>
    @endif
    <p><a href="/ejecuciones/${a.guardado().ejecucionId().toString()}">Abrir la ejecución que lo produjo</a></p>
  </section>
`)
