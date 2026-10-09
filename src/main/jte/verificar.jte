@import pensamiento.web.Pagina
@import pensamiento.web.verificacion.VistaIndice
@import pensamiento.web.verificacion.VistaFicha
@import pensamiento.nucleo.PendienteGuardado
@param Pagina pagina
@param VistaIndice v
@template.layout(pagina = pagina, contenido = @`
  <section class="verificacion-indice" aria-labelledby="verificar-titulo">
    <h1 id="verificar-titulo">Verificación</h1>
    <p class="detalle">La ficha de verificación se abre desde una premisa de un argumento guardado (botón «Verificar» en el argumento) o desde
      un pendiente de verificación. La app no dice si algo es cierto: guía la verificación, guarda el rastro y recalcula el argumento.</p>
    <section aria-labelledby="por-verificar-titulo">
      <h2 id="por-verificar-titulo">Por verificar</h2>
      @if(v.pendientes().isEmpty())
        <p class="detalle">No tienes pendientes de verificación.</p>
      @else
        <ul class="pendientes">
          @for(PendienteGuardado p : v.pendientes())
            <li><span class="chip chip-aviso">verificación</span>
              <a href="/verificar/${p.pendiente().objetoId().get().toString()}">${p.pendiente().descripcion()}</a></li>
          @endfor
        </ul>
      @endif
    </section>
    <section aria-labelledby="trabajadas-titulo">
      <h2 id="trabajadas-titulo">Fichas trabajadas</h2>
      @if(v.trabajadas().isEmpty())
        <p class="detalle">Todavía no trabajaste ninguna ficha.</p>
      @else
        <ul class="fichas-trabajadas">
          @for(VistaIndice.Trabajada t : v.trabajadas())
            <li><span class="${VistaFicha.claseEstado(t.afirmacion().estado())}">${VistaFicha.estado(t.afirmacion().estado())}</span>
              <a href="/verificar/${t.afirmacion().id().toString()}">${t.afirmacion().texto()}</a></li>
          @endfor
        </ul>
      @endif
    </section>
    <p><a href="/biblioteca">Ir a la biblioteca</a></p>
  </section>
`)
