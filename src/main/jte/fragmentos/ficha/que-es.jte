@import pensamiento.web.tecnicas.ControladorTecnicas.VistaFicha
@import pensamiento.web.tecnicas.ControladorTecnicas.Relacionada
@import pensamiento.web.tecnicas.ControladorTecnicas.Pestana
@import pensamiento.nucleo.Tecnica
@param VistaFicha ficha
!{Tecnica t = ficha.tecnica();}
<article class="que-es">
  <h2>Qué es</h2>
  <p class="definicion">${t.definicion()}</p>
  <h3>Úsala cuando</h3>
  <p class="usala">${t.usalaCuando()}</p>
  <dl class="facetas">
    <dt>Origen</dt><dd>${t.origen()}</dd>
    <dt>Tipo</dt><dd>${t.tipo().name().toLowerCase().replace('_', ' ')}</dd>
    <dt>Operación</dt><dd>${t.operacion().name().toLowerCase()}</dd>
    <dt>Sobre qué trabaja</dt><dd>${t.objeto().name().toLowerCase().replace('_', ' ')}</dd>
    <dt>Forma</dt><dd>${t.modalidad().name().toLowerCase()}</dd>
  </dl>
  @if(!ficha.relacionadas().isEmpty())
    <h3>Técnicas relacionadas</h3>
    <ul class="relacionadas">
      @for(Relacionada r : ficha.relacionadas())
        <li>${r.frase()} <a href="/tecnicas/${r.tecnica().id().valor()}">${r.tecnica().cita()}</a></li>
      @endfor
    </ul>
  @endif
  @if(ficha.activa())
    <p><a class="boton" href="${ficha.url(Pestana.USAR)}" hx-get="${ficha.url(Pestana.USAR)}" hx-target="#ficha-cuerpo" hx-swap="outerHTML" hx-push-url="true">Usar esta técnica</a></p>
  @else
    <p class="detalle">La pestaña Usar de esta técnica llega en el hito ${ficha.hito()}.</p>
  @endif
</article>
