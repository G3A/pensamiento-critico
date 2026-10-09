@import pensamiento.web.Pagina
@import pensamiento.web.consejero.VistaConsejero
@import pensamiento.nucleo.Expediente
@param Pagina pagina
@param VistaConsejero.Sesion v
@template.layout(pagina = pagina, contenido = @`
  <section class="consejero-sesion" aria-labelledby="sesion-titulo" data-sesion="${v.sesion().id().toString()}" data-modo="${v.sesion().modo().toString()}">
    <p class="migas"><a href="/consejero">Consejero</a> › sesión</p>
    <h1 id="sesion-titulo">${v.titulo()}</h1>
    <p class="detalle"><span class="chip">modo ${v.sesion().modo().nombre()}</span>
      @if(v.sesion().usaModelo() && v.modeloDisponible())
        <span class="chip">el modelo redacta, el código elige</span>
      @else
        <span class="chip chip-aviso">modo plantillas</span> Las preguntas salen del banco.
      @endif
      @if(v.expediente().isPresent())
        · Se guarda en <a href="/expedientes/${v.expediente().get().id().toString()}">${v.expediente().get().nombre()}</a>
      @endif
    </p>
    @if(!v.error().isEmpty())
      <p class="error" role="alert">${v.error()}</p>
    @endif

    <div class="transcripcion-con-panel">
      <div class="columna-transcripcion">
        <ol id="dialogo" class="transcripcion" role="log" aria-label="Diálogo con el Consejero" aria-live="polite">
          @for(VistaConsejero.Burbuja b : v.burbujas())
            @template.fragmentos.consejero.burbuja(b = b)
          @endfor
        </ol>
        <p id="mensaje-consejero" class="error" role="alert"></p>
        @template.fragmentos.consejero.entrada(v = v, oob = false)
      </div>
      @template.fragmentos.consejero.panel(v = v, oob = false)
    </div>

    @if(v.debate() && !v.cerrada())
      <section class="despues-del-equipo-rojo" aria-labelledby="debate-titulo">
        <h2 id="debate-titulo">Después del equipo rojo</h2>
        <p class="detalle">Cada técnica se guarda en el expediente de esta sesión.</p>
        <nav class="consejero-modos" aria-label="Técnicas del debate">
          <a class="boton boton-secundario" href="#zona-debate" hx-get="${v.base()}/debate?tecnica=T34" hx-target="#zona-debate" hx-swap="innerHTML">T34 · Steelmanning</a>
          <a class="boton boton-secundario" href="#zona-debate" hx-get="${v.base()}/debate?tecnica=T37" hx-target="#zona-debate" hx-swap="innerHTML">T37 · Test de Turing ideológico</a>
          <a class="boton boton-secundario" href="#zona-debate" hx-get="${v.base()}/debate?tecnica=T38" hx-target="#zona-debate" hx-swap="innerHTML">T38 · Double crux</a>
        </nav>
        <div id="zona-debate"></div>
      </section>
    @endif

    @if(v.resultado().isPresent())
      <section class="resultado-sesion" aria-labelledby="resultado-sesion-titulo">
        <h2 id="resultado-sesion-titulo">Lo que quedó guardado</h2>
        @if(v.sesion().reflexion().isPresent())
          <p class="enunciado">Qué cambió: ${v.sesion().reflexion().get()}</p>
        @endif
        ${v.resultado().get()}
      </section>
    @endif
  </section>
`)
