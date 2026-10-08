@import pensamiento.web.Pagina
@import pensamiento.web.diario.ControladorDiario
@import pensamiento.flujos.DiarioDeDecisiones
@import pensamiento.nucleo.Expediente
@import pensamiento.nucleo.Prediccion
@param Pagina pagina
@param ControladorDiario.VistaTablero v
@template.layout(pagina = pagina, contenido = @`
  <section class="diario" aria-labelledby="diario-titulo">
    <h1 id="diario-titulo">Diario de decisiones</h1>
    <p class="detalle">Registras cada decisión con una predicción en porcentaje; en la fecha la revisas y ves qué tan calibrado estás.
      T32 · Diario de decisiones y T25 · Calibración y puntaje Brier.</p>

    <form method="post" action="/diario/decisiones" class="nueva-decision" novalidate>
      <input type="hidden" name="_csrf" value="${pagina.csrf()}">
      <label for="titulo-decision">¿Qué tienes que decidir?</label>
      <input id="titulo-decision" name="titulo" type="text" maxlength="180" value="${v.titulo()}"
             aria-describedby="${v.error().isEmpty() ? null : "error-diario"}" placeholder="Abrir la segunda sucursal">
      <button type="submit" class="boton" data-accion="nueva-decision">Nueva decisión</button>
      @if(!v.error().isEmpty())
        <p id="error-diario" class="error-campo" role="alert"><span class="chip chip-error">error</span> ${v.error()}</p>
      @endif
    </form>

    <section id="revisiones" class="tarjeta" aria-labelledby="revisiones-titulo">
      <h2 id="revisiones-titulo">Revisiones pendientes (${v.tablero().porRevisar().size()})</h2>
      @if(v.tablero().porRevisar().isEmpty())
        <p class="detalle">Nada por revisar hoy.</p>
      @else
        <ul class="lista-decisiones">
          @for(DiarioDeDecisiones.Decision d : v.tablero().porRevisar())
            !{Prediccion p = d.prediccion();}
            <li data-prediccion="${p.id().toString()}">
              <p><span class="chip chip-aviso">revisar hoy</span> <strong>${d.decision()}</strong></p>
              <p>Predijiste: «${p.texto()}» · <span class="chip">${p.confianza()}%</span> · tocaba revisar el ${d.revisarEl()}.</p>
              <form method="post" action="/diario/predicciones/${p.id().toString()}/resolucion" class="revisar">
                <input type="hidden" name="_csrf" value="${pagina.csrf()}">
                <button type="submit" name="resultado" value="se_cumplio" class="boton" data-accion="se-cumplio">Se cumplió</button>
                <button type="submit" name="resultado" value="no_se_cumplio" class="boton boton-secundario" data-accion="no-se-cumplio">No se cumplió</button>
              </form>
              <p class="detalle">El resultado queda fijo: después no se puede cambiar.</p>
            </li>
          @endfor
        </ul>
      @endif
    </section>

    <div class="diario-columnas">
      <section aria-labelledby="abiertas-titulo">
        <h2 id="abiertas-titulo">Decisiones abiertas (${v.tablero().abiertas().size()})</h2>
        @if(v.tablero().abiertas().isEmpty())
          <p class="detalle">No hay decisiones esperando su fecha.</p>
        @else
          <ul class="lista-decisiones">
            @for(DiarioDeDecisiones.Decision d : v.tablero().abiertas())
              <li data-prediccion="${d.prediccion().id().toString()}">
                <span class="chip">${d.prediccion().confianza()}%</span>
                <a href="${d.expedienteId().map(x -> "/diario/decisiones/" + x).orElse("/ejecuciones/" + d.prediccion().ejecucionId())}">${d.decision()}</a>
                <span class="detalle">· revisar el ${d.revisarEl()}</span>
                @if(d.historial().isPresent())
                  <p class="detalle"><span class="chip chip-aviso">aviso</span> ${d.historial().get()}</p>
                @endif
              </li>
            @endfor
          </ul>
        @endif
        @if(!v.tablero().enPreparacion().isEmpty())
          <h3 class="subtitulo">En preparación</h3>
          <ul>
            @for(Expediente x : v.tablero().enPreparacion())
              <li><a href="/diario/decisiones/${x.id().toString()}">${DiarioDeDecisiones.titulo(x)}</a> <span class="chip chip-pendiente">sin registrar</span></li>
            @endfor
          </ul>
        @endif
      </section>
      <section id="calibracion" aria-labelledby="calibracion-titulo">
        <h2 id="calibracion-titulo">Calibración</h2>
        @if(v.calibracion().isPresent())
          @template.tag.v.v08(v = v.calibracion().get())
        @else
          <p class="detalle">Cuando revises tu primera decisión, aquí aparecen tu puntaje Brier y tu curva de calibración.</p>
        @endif
      </section>
    </div>

    @if(!v.tablero().resueltas().isEmpty())
      <section aria-labelledby="resueltas-titulo">
        <h2 id="resueltas-titulo">Resueltas (${v.tablero().resueltas().size()})</h2>
        <ul class="lista-decisiones">
          @for(DiarioDeDecisiones.Decision d : v.tablero().resueltas())
            <li data-prediccion="${d.prediccion().id().toString()}">
              <span class="${d.prediccion().estado() == Prediccion.Estado.ACIERTO ? "chip chip-ok" : "chip chip-aviso"}">${d.prediccion().estado() == Prediccion.Estado.ACIERTO ? "se cumplió" : "no se cumplió"}</span>
              <span class="chip">${d.prediccion().confianza()}%</span>
              <a href="${d.expedienteId().map(x -> "/diario/decisiones/" + x).orElse("/ejecuciones/" + d.prediccion().ejecucionId())}">${d.decision()}</a>
            </li>
          @endfor
        </ul>
      </section>
    @endif
  </section>
`)
