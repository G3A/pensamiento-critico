@import pensamiento.web.tecnicas.ControladorTecnicas.VistaFicha
@import pensamiento.web.tecnicas.ControladorTecnicas.Pestana
@import pensamiento.nucleo.Tecnica
@param VistaFicha ficha
!{Tecnica t = ficha.tecnica();}
<section id="ficha-cuerpo" class="ficha" data-tecnica="${t.id().valor()}" aria-labelledby="ficha-titulo">
  <header class="cabecera-ficha">
    <p class="familia-ficha"><a href="/catalogo?familia=${t.familia()}">${ficha.familia()}</a></p>
    <h1 id="ficha-titulo">${t.nombreLlano()}</h1>
    <p class="cita">${t.cita()}</p>
    <p class="chips">
      <span class="chip">${t.modalidad().name().toLowerCase()}</span>
      @if(t.requiereIa() == Tecnica.RequiereIa.NO)
        <span class="chip chip-ok">sin IA</span>
      @elseif(t.requiereIa() == Tecnica.RequiereIa.OPCIONAL)
        <span class="chip">Ollama opcional</span>
      @else
        <span class="chip chip-aviso">necesita Ollama</span>
      @endif
      @if(ficha.activa())
        <span class="chip chip-ok">disponible</span>
      @else
        <span class="chip chip-pendiente">llega en el hito ${ficha.hito()}</span>
      @endif
    </p>
    @if(ficha.activa())
      <p id="resumen-config-${t.id().valor()}" class="resumen-config">Tu configuración: ${ficha.resumenConfig()}</p>
    @endif
  </header>
  <div class="pestanas" role="tablist" aria-label="Pestañas de ${t.nombreLlano()}">
    @for(Pestana p : ficha.pestanas())
      <a role="tab" id="pestana-${p.clave()}" href="${ficha.url(p)}" hx-get="${ficha.url(p)}" hx-target="#ficha-cuerpo" hx-swap="outerHTML"
         hx-push-url="true" aria-controls="panel-ficha" aria-selected="${p == ficha.pestana() ? "true" : "false"}"
         tabindex="${p == ficha.pestana() ? "0" : "-1"}" class="pestana">${p.titulo()}</a>
    @endfor
  </div>
  <div id="panel-ficha" class="panel-ficha" role="tabpanel" aria-labelledby="pestana-${ficha.pestana().clave()}" tabindex="0">
    @if(ficha.pestana() == Pestana.QUE_ES)
      @template.fragmentos.ficha.que-es(ficha = ficha)
    @elseif(!ficha.activa())
      <div class="tarjeta vacio">
        <p><strong>${t.cita()}</strong> todavía no se puede usar: su pestaña Usar llega en el hito ${ficha.hito()}.</p>
        <p>Mientras tanto, lee qué es y cuándo usarla en la pestaña <a href="${ficha.url(Pestana.QUE_ES)}">Qué es</a>.</p>
      </div>
    @elseif(ficha.pestana() == Pestana.USAR)
      @template.fragmentos.ficha.usar(u = ficha.usar())
    @else
      @template.fragmentos.ficha.historial(ficha = ficha)
    @endif
  </div>
</section>
