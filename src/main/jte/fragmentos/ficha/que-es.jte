@import pensamiento.web.tecnicas.ControladorTecnicas.VistaFicha
@import pensamiento.web.tecnicas.ControladorTecnicas.Relacionada
@import pensamiento.web.tecnicas.ControladorTecnicas.Pestana
@import pensamiento.nucleo.Ejemplo
@import pensamiento.nucleo.Tecnica
@param VistaFicha ficha
!{Tecnica t = ficha.tecnica();}
<article class="que-es">
  <h2 class="visualmente-oculto">Qué es</h2>
  <p class="para-que">${t.usalaCuando()}</p>
  @if(ficha.activa())
    <p class="acciones"><a class="boton" href="${ficha.url(Pestana.USAR)}" hx-get="${ficha.url(Pestana.USAR)}" hx-target="#ficha-cuerpo" hx-swap="outerHTML"
          hx-push-url="true" data-accion="caso-propio">Empezar con mi propio caso</a></p>
  @else
    <p class="detalle">La pestaña Usar de esta técnica llega en el hito ${ficha.hito()}.</p>
  @endif
  @if(!ficha.ejemplos().isEmpty())
    <h3>Pruébala con un ejemplo</h3>
    <ul class="ejemplos-ficha">
      @for(Ejemplo e : ficha.ejemplos())
        <li class="tarjeta">
          <span class="chip chip-pendiente">${e.ambito().titulo()}</span>
          <strong>${e.titulo()}</strong>
          @if(e.nota() != null && !e.nota().isBlank())
            <span class="detalle">${e.nota()}</span>
          @endif
          <a href="${ficha.url(e)}" hx-get="${ficha.url(e)}" hx-target="#ficha-cuerpo" hx-swap="outerHTML" hx-push-url="true">Probar este ejemplo</a>
        </li>
      @endfor
    </ul>
  @endif
  @if(!ficha.relacionadas().isEmpty())
    <h3>Técnicas relacionadas</h3>
    <ul class="relacionadas">
      @for(Relacionada r : ficha.relacionadas())
        <li>${r.frase()} <a href="/tecnicas/${r.tecnica().id().valor()}">${r.tecnica().cita()}</a></li>
      @endfor
    </ul>
  @endif
  <details class="ficha-tecnica">
    <summary>Ficha técnica, para quien quiera profundizar</summary>
    <p class="definicion">${t.definicion()}</p>
    <dl class="facetas">
      <dt>Nombre técnico</dt><dd>${t.cita()}</dd>
      <dt>Origen</dt><dd>${t.origen()}</dd>
      <dt>Tipo</dt><dd>${VistaFicha.faceta(t.tipo())}</dd>
      <dt>Operación</dt><dd>${VistaFicha.faceta(t.operacion())}</dd>
      <dt>Sobre qué trabaja</dt><dd>${VistaFicha.faceta(t.objeto())}</dd>
      <dt>Forma</dt><dd>${VistaFicha.faceta(t.modalidad())}</dd>
    </dl>
  </details>
</article>
