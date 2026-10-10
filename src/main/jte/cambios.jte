@import pensamiento.web.Pagina
@import pensamiento.web.cambios.ControladorCambios
@import pensamiento.nucleo.CambioOpinion
@param Pagina pagina
@param ControladorCambios.VistaCambios v
@template.layout(pagina = pagina, contenido = @`
  <section class="cambios" aria-labelledby="cambios-titulo">
    <h1 id="cambios-titulo">Cambios de opinión</h1>
    <nav class="temas-dojo" aria-label="Registro y diario">
      <ul>
        <li><a href="/cambios-de-opinion" aria-current="page" class="activo">Cambios de opinión</a></li>
        <li><a href="/cambios-de-opinion/diario">Diario de razonamiento</a></li>
      </ul>
    </nav>
    <p class="detalle">T46 · Registro de cambios de opinión reúne en una línea de tiempo los cambios de confianza que registran
      T08 · Preguntas socráticas, el Consejero y la ficha de verificación, y los que anotas aquí. R05 · Confianza y calibración: la
      confianza la declaras tú; cada cambio guarda el antes, el después y la causa, y no se borra.</p>
    @if(!v.registroAutomatico())
      <p class="aviso-lista aviso-nota"><span class="chip chip-aviso">aviso</span> El registro automático está apagado en tu configuración de T46:
        solo cuentan los cambios que registras a mano. <a href="/tecnicas/T46?pestana=usar">Cambiarlo</a></p>
    @endif

    @template.tag.v.v11(v = v.registro())

    <section class="tarjeta" aria-labelledby="sin-revisar-titulo">
      <h2 id="sin-revisar-titulo">${v.tituloSinRevisar()}</h2>
      @if(v.sinRevisar().isEmpty())
        <p class="detalle">Ninguna: todas tus posturas se trabajaron hace poco.</p>
      @else
        <ul class="lista-aparte">
          @for(ControladorCambios.SinRevisar s : v.sinRevisar())
            <li>${s.texto()} · <a href="${s.debate()}">Ponerla a prueba en el modo debate</a></li>
          @endfor
        </ul>
      @endif
    </section>

    <section class="tarjeta" aria-labelledby="registrar-titulo">
      <h2 id="registrar-titulo">Registrar un cambio a mano</h2>
      <form method="post" action="/cambios-de-opinion" hx-post="/cambios-de-opinion" hx-target="#principal" hx-select="#principal" hx-swap="outerHTML"
            class="registro-cambio" novalidate>
        <input type="hidden" name="_csrf" value="${pagina.csrf()}">
        <input type="hidden" name="_clave" value="${v.clave()}">
        <label for="nueva-postura">La postura que cambió</label>
        <input id="nueva-postura" name="postura" type="text" maxlength="200" list="posturas-conocidas" value="${v.valores().postura()}"
               aria-describedby="${v.errores().containsKey("nuevaPostura") ? "error-nueva-postura" : null}">
        <datalist id="posturas-conocidas">
          @for(String p : v.posturas())
            <option value="${p}"></option>
          @endfor
        </datalist>
        @if(v.errores().containsKey("nuevaPostura"))
          <p id="error-nueva-postura" class="error-campo" role="alert"><span class="chip chip-error">error</span> ${v.errores().get("nuevaPostura")}</p>
        @endif
        <label for="nueva-antes">Tu confianza antes (%)</label>
        <input id="nueva-antes" name="antes" type="number" min="0" max="100" inputmode="numeric" value="${v.valores().antes()}">
        <label for="nueva-despues">Tu confianza ahora (%)</label>
        <input id="nueva-despues" name="despues" type="number" min="0" max="100" inputmode="numeric" value="${v.valores().despues()}"
               aria-describedby="${v.errores().containsKey("nuevaDespues") ? "error-nueva-despues" : null}">
        @if(v.errores().containsKey("nuevaDespues"))
          <p id="error-nueva-despues" class="error-campo" role="alert"><span class="chip chip-error">error</span> ${v.errores().get("nuevaDespues")}</p>
        @endif
        <label for="nueva-causa">¿Qué te movió?</label>
        <select id="nueva-causa" name="causa" aria-describedby="${v.errores().containsKey("nuevaCausa") ? "error-nueva-causa" : null}">
          <option value="">Elige la causa</option>
          @for(CambioOpinion.Causa c : v.causas())
            <option value="${c.toString()}" selected="${c.toString().equals(v.valores().causa())}">${c.nombre()}</option>
          @endfor
        </select>
        @if(v.errores().containsKey("nuevaCausa"))
          <p id="error-nueva-causa" class="error-campo" role="alert"><span class="chip chip-error">error</span> ${v.errores().get("nuevaCausa")}</p>
        @endif
        <button type="submit" class="boton" data-accion="registrar-cambio">Registrar el cambio</button>
      </form>
    </section>
  </section>
`)
