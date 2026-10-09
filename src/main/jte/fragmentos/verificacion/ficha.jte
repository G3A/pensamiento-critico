@import pensamiento.web.verificacion.VistaFicha
@import pensamiento.flujos.FichaDeVerificacion
@import pensamiento.nucleo.EstandarPrueba
@import pensamiento.nucleo.PendienteGuardado
@param VistaFicha f
<div id="ficha" class="ficha-pasos">
  @if(!f.mensaje().isEmpty())
    <p class="aviso-ia mensaje-ficha" role="status"><span class="chip chip-ok">listo</span> ${f.mensaje()}</p>
  @endif
  <section class="paso-ficha tarjeta" aria-labelledby="afirmacion-titulo">
    <h2 id="afirmacion-titulo">La afirmación</h2>
    <blockquote class="afirmacion-ficha">«${f.f().afirmacion().texto()}»</blockquote>
    <p class="detalle">Estado guardado: <span class="${VistaFicha.claseEstado(f.f().afirmacion().estado())}" data-estado-guardado>${VistaFicha.estado(f.f().afirmacion().estado())}</span>
      @if(f.f().afirmacion().confianza().isPresent()) · tu confianza: ${f.f().afirmacion().confianza().get()}%@endif
      @if(f.f().origen().isPresent()) · viene de <a href="/ejecuciones/${f.f().origen().get().ejecucionId().toString()}">${f.f().origen().get().tecnica().valor()}</a>@endif</p>
  </section>

  <section class="paso-ficha tarjeta" aria-labelledby="paso1-titulo">
    <h2 id="paso1-titulo">1 · Tipo de afirmación</h2>
    <form class="tipo-afirmacion" method="post" action="/verificar/${f.id()}/tipo" hx-post="/verificar/${f.id()}/tipo" hx-target="#ficha" hx-swap="outerHTML">
      <fieldset class="campo">
        <legend>Qué tipo de afirmación es (T17 · Hecho, inferencia, juicio)</legend>
        <div class="radios">
          @for(FichaDeVerificacion.TipoInfo t : f.f().tipos())
            <label class="radio"><input type="radio" name="tipo" value="${t.tipo().enBaseDeDatos()}" checked="${t.tipo() == f.f().tipo().tipo()}"> ${t.nombre()}</label>
          @endfor
        </div>
      </fieldset>
      <button type="submit" class="boton boton-secundario boton-chico">Guardar el tipo</button>
    </form>
    <p class="${f.f().tipo().verificable() ? "detalle" : "aviso-ia"}" data-verificable="${String.valueOf(f.f().tipo().verificable())}">
      @if(f.f().tipo().verificable())<span class="chip chip-ok">verificable</span>@else<span class="chip chip-aviso">no verificable</span>@endif
      ${f.f().tipo().nombre()}. Lo probaría: ${f.f().tipo().probaria()}.</p>
    <h3 class="subtitulo">Chequeos automáticos (por reglas)</h3>
    <ul class="chequeos">
      @for(FichaDeVerificacion.Chequeo c : f.f().chequeos())
        <li data-chequeo="${c.nombre()}"><span class="${VistaFicha.claseChequeo(c)}">${c.estado()}</span> <strong>${c.nombre()}:</strong> ${c.texto()}</li>
      @endfor
    </ul>
  </section>

  <section class="paso-ficha tarjeta" aria-labelledby="paso2-titulo">
    <h2 id="paso2-titulo">2 · Preguntas críticas</h2>
    <p class="detalle">@if(f.f().esquema().isPresent())Del esquema «${f.f().esquema().get()}» del argumento.@else Las de un ${f.f().tipo().nombre().toLowerCase()}: el argumento no trae esquema de Walton.@endif
      Marca las que ya respondiste.</p>
    <form class="preguntas-criticas" method="post" action="/verificar/${f.id()}/preguntas" hx-post="/verificar/${f.id()}/preguntas" hx-target="#ficha"
          hx-swap="outerHTML">
      <ul class="lista-verificacion">
        @for(FichaDeVerificacion.Pregunta p : f.f().preguntas())
          <li><label class="casilla"><input type="checkbox" name="respondida" value="${p.texto()}" checked="${p.respondida()}"> ${p.texto()}</label>
            <span class="${p.respondida() ? "chip chip-ok" : "chip chip-pendiente"}">${p.respondida() ? "respondida" : "sin responder"}</span></li>
        @endfor
      </ul>
      <button type="submit" class="boton boton-secundario boton-chico">Guardar las preguntas</button>
    </form>
  </section>

  <section class="paso-ficha tarjeta" aria-labelledby="paso3-titulo">
    <h2 id="paso3-titulo">3 · Evidencia registrada</h2>
    @if(f.f().evidencias().isEmpty())
      <p class="detalle">Todavía no registraste fuentes. Busca en tu biblioteca o añade una fuente que encontraste afuera.</p>
    @else
      <ol class="evidencias">
        @for(VistaFicha.EvidenciaVista e : f.evidencias())
          <li data-evidencia="${e.id()}">
            <p><span class="${e.clasePostura()}">${e.postura()}</span> <strong>${e.titulo()}</strong> · <span class="fuerza">${e.fuerza()}</span></p>
            <p class="detalle">${e.detalle()} · @if(e.enlaceOriginal() != null)<a href="${e.enlaceOriginal()}">${e.ubicacion()}</a>@else${e.ubicacion()}@endif</p>
            <blockquote class="pasaje">«${e.pasaje()}»</blockquote>
            @for(String nota : e.sift())
              <p class="detalle">${nota}</p>
            @endfor
            <button type="button" class="boton boton-secundario boton-chico" hx-post="/verificar/${f.id()}/evidencias/${e.id()}/quitar" hx-target="#ficha"
                    hx-swap="outerHTML" hx-confirm="¿Quitar esta evidencia de la ficha?">Quitar</button>
          </li>
        @endfor
      </ol>
    @endif
    @if(f.f().admiteMasFuentes())
      <p><a class="boton" href="/verificar/${f.id()}/fuentes/nueva">Añadir fuente</a></p>
    @else
      <p class="detalle">La ficha ya tiene seis fuentes, el máximo de T22 · Triangulación.</p>
    @endif
  </section>

  <section class="paso-ficha tarjeta veredicto-ficha" aria-labelledby="paso4-titulo">
    <h2 id="paso4-titulo">4 · Veredicto</h2>
    <p class="estado-resultado"><span class="${VistaFicha.claseEstado(f.f().calculo().estado())}" data-estado-calculado>${VistaFicha.estado(f.f().calculo().estado())}</span>
      <span class="firma">${f.f().calculo().firma()}</span></p>
    <p class="barra-puntaje"><label for="neta-${f.id()}">Fuerza neta ${f.neta()} (${f.f().calculo().magnitud()}) · R02</label>
      <meter id="neta-${f.id()}" min="${-f.maximoNeta()}" max="${f.maximoNeta()}" value="${f.f().calculo().neta()}">${f.neta()}</meter></p>
    @if(f.f().preguntasSinResponder() > 0)
      <p class="detalle">${f.f().preguntasSinResponder()} ${f.f().preguntasSinResponder() == 1 ? "pregunta crítica sin responder" : "preguntas críticas sin responder"}.</p>
    @endif
    <form class="guardar-veredicto" method="post" action="/verificar/${f.id()}/veredicto" hx-post="/verificar/${f.id()}/veredicto" hx-target="#ficha"
          hx-swap="outerHTML" hx-disabled-elt="find button">
      <input type="hidden" name="clave" value="${f.clave()}">
      <label for="confianza-${f.id()}">Tu confianza en la afirmación (0 a 100)</label>
      <input type="number" id="confianza-${f.id()}" name="confianza" min="0" max="100" step="1" value="${f.confianza()}">
      <p class="detalle">Si ya habías declarado una confianza y ahora es otra, queda como cambio de opinión con causa «evidencia».</p>
      <button type="submit" class="boton">Guardar veredicto</button>
    </form>
  </section>

  @if(!f.f().argumentos().isEmpty())
    <section class="paso-ficha tarjeta" aria-labelledby="r04-titulo">
      <h2 id="r04-titulo">Estándar de prueba de los argumentos (R04)</h2>
      @for(FichaDeVerificacion.R04Recalculado r : f.f().argumentos())
        <div class="r04" data-argumento="${r.argumentoId().toString()}">
          <p><strong>«${r.conclusion()}»</strong> · estándar del argumento: ${r.estandar().nombre()} ·
            <span class="${r.aceptable().get(r.estandar()) ? "chip chip-ok" : "chip chip-aviso"}" data-aceptable="${String.valueOf(r.aceptable().get(r.estandar()))}">${r.aceptable().get(r.estandar()) ? "se cumple" : "no se cumple"}</span></p>
          <p class="detalle">${r.frase()} El argumento ${r.aplicable() ? "es aplicable" : "no es aplicable todavía"} con lo que verificaste.</p>
          <div class="tabla-desplazable">
            <table class="tabla tabla-estandares">
              <caption class="solo-lector">Aceptable bajo cada estándar</caption>
              <thead><tr><th scope="col">Estándar</th><th scope="col">Aceptable</th></tr></thead>
              <tbody>
                @for(EstandarPrueba e : f.estandares())
                  <tr data-estandar="${e.name().toLowerCase()}"><th scope="row">${e.nombre()}</th><td>${VistaFicha.si(r.aceptable().get(e))}</td></tr>
                @endfor
              </tbody>
            </table>
          </div>
          <p><a href="/argumentos/${r.argumentoId().toString()}">Abrir el argumento</a></p>
        </div>
      @endfor
    </section>
  @endif

  @if(!f.f().pendientes().isEmpty())
    <section class="paso-ficha tarjeta" aria-labelledby="pendientes-ficha-titulo">
      <h2 id="pendientes-ficha-titulo">Pendientes sobre esta afirmación</h2>
      <ul class="pendientes">
        @for(PendienteGuardado p : f.f().pendientes())
          <li><span class="chip chip-aviso">${p.pendiente().tipo().name().toLowerCase()}</span> ${p.pendiente().descripcion()}</li>
        @endfor
      </ul>
      <p class="detalle">Guardar el veredicto cierra los de verificación y de revisión.</p>
    </section>
  @endif
</div>
