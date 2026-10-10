@import pensamiento.web.Pagina
@import pensamiento.web.dojo.ControladorDojo
@import pensamiento.flujos.DojoDeRazonamiento
@import pensamiento.nucleo.BancoDojo
@param Pagina pagina
@param ControladorDojo.VistaDojo v
<section id="reto" class="reto tarjeta" aria-labelledby="reto-titulo">
  @if(v.p().reto().isPresent())
    !{DojoDeRazonamiento.RetoElegido e = v.p().reto().get();}
    !{BancoDojo.Reto r = e.reto();}
    <p class="detalle" data-reto="${r.id()}">
      <span class="chip chip-pendiente">${r.nivel().toString()}</span>
      ${e.tema().nombre()} · ${e.repaso() ? "repaso que toca hoy" : "concepto nuevo"} · ${r.nivel().quePide()}
    </p>
    <h2 id="reto-titulo">${r.pregunta()}</h2>
    <blockquote class="texto-reto"><p>${r.texto()}</p></blockquote>
    <form method="post" action="/dojo/retos/${r.id()}" hx-post="/dojo/retos/${r.id()}" hx-target="#reto" hx-swap="outerHTML" novalidate
          class="formulario-reto">
      <input type="hidden" name="_csrf" value="${pagina.csrf()}">
      <input type="hidden" name="_clave" value="${v.clave()}">
      <input type="hidden" name="tema" value="${v.claveTema()}">
      <input type="hidden" name="nivel" value="${v.p().nivel().toString()}">
      @if(r.esDeEscribir())
        <label for="respuesta-reto">Tu versión</label>
        <textarea id="respuesta-reto" name="respuesta" maxlength="1000" rows="4"
                  aria-describedby="${v.error().isEmpty() ? "ayuda-reto" : "error-reto ayuda-reto"}"></textarea>
        <p id="ayuda-reto" class="detalle">La revisa una rúbrica por palabras, no un modelo: mira si quitaste el error y si dices lo que falta.</p>
      @else
        <fieldset aria-describedby="${v.error().isEmpty() ? null : "error-reto"}">
          <legend>Elige una opción</legend>
          @for(BancoDojo.Opcion o : r.opciones())
            <label class="opcion-reto"><input type="radio" name="respuesta" value="${o.id()}"> ${o.texto()}</label>
          @endfor
        </fieldset>
      @endif
      @if(!v.error().isEmpty())
        <p id="error-reto" class="error-campo" role="alert"><span class="chip chip-error">error</span> ${v.error()}</p>
      @endif
      <button type="submit" class="boton" data-accion="responder">Responder</button>
    </form>
  @else
    <h2 id="reto-titulo">Por ahora no hay reto</h2>
    <p class="mensaje-dojo" role="status">${v.p().mensaje().orElse("")}</p>
    <p><a href="/dojo/progreso" class="boton boton-secundario">Ver tu progreso y el calendario</a></p>
  @endif
</section>
