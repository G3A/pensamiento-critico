@import pensamiento.web.tecnicas.ControladorTecnicas.VistaUsar
@import pensamiento.nucleo.Ejemplo
@param VistaUsar u
!{String id = u.tecnica().id().valor();}
<div class="usar">
  <nav class="barra-ejemplos" aria-label="Ejemplos de ${u.tecnica().nombreLlano()}">
    <h2 class="subtitulo">Ejemplos (${u.ejemplos().size()})</h2>
    <ul>
      @for(Ejemplo e : u.ejemplos())
        <li>
          <a href="/tecnicas/${id}?ejemplo=${e.id().toString()}" hx-get="/tecnicas/${id}?ejemplo=${e.id().toString()}" hx-target="#ficha-cuerpo"
             hx-swap="outerHTML" hx-push-url="true" class="ejemplo ${u.ejemploElegido().map(x -> x.id().equals(e.id())).orElse(false) ? "activo" : ""}"
             aria-current="${u.ejemploElegido().map(x -> x.id().equals(e.id())).orElse(false) ? "true" : null}"
             data-ejemplo="${e.orden()}">${e.orden()} · ${e.ambito().titulo()}: ${e.titulo()}</a>
        </li>
      @endfor
    </ul>
    <p class="detalle">Elegir un ejemplo carga su configuración y sus datos en el formulario para que los modifiques.</p>
  </nav>

  @if(u.ejemploElegido().isPresent())
    !{Ejemplo e = u.ejemploElegido().get();}
    <article class="ejemplo-elegido tarjeta" aria-labelledby="ejemplo-titulo">
      <h3 id="ejemplo-titulo">Ejemplo ${e.orden()} de ${u.ejemplos().size()} · ${e.ambito().titulo()}: ${e.titulo()}</h3>
      <p><strong>Datos ficticios.</strong> ${e.nota()}</p>
      <p><strong>Configuración usada.</strong> ${u.formulario().resumenConfig()}</p>
      <details>
        <summary>Resultado del ejemplo</summary>
        @if(u.resultadoEjemplo().isPresent())
          ${u.resultadoEjemplo().get()}
        @endif
      </details>
    </article>
  @endif

  <details class="configuracion">
    <summary>Configuración · <span class="detalle">${u.configuracion().resumen()}</span></summary>
    @template.fragmentos.ficha.configuracion(c = u.configuracion())
  </details>

  <div class="usar-cuerpo">
    @template.fragmentos.ficha.formulario(f = u.formulario(), oob = false)
    <div id="${u.formulario().idEspera()}" class="zona-espera" aria-live="polite"></div>
    <div id="${u.formulario().idResultado()}" class="zona-resultado" aria-live="polite">
      <p class="detalle">El resultado aparece aquí al evaluar.</p>
    </div>
  </div>
</div>
