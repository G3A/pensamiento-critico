@import pensamiento.web.manual.ManualPorFamilia
@param ManualPorFamilia.Capitulo c
<article class="manual-capitulo" aria-labelledby="capitulo-titulo" data-familia="${c.familia().codigo()}">
  <h1 id="capitulo-titulo">${c.familia().codigo()} · ${c.familia().nombre()}</h1>
  <p class="detalle">Capítulo del manual del Taller de Pensamiento Crítico, generado desde el catálogo: ${c.tecnicas().size()} técnicas, cada
    una con su ficha "Qué es", su configuración de fábrica y sus ejemplos de la familia, la panadería y el barrio, con el resultado tal como
    lo muestra la aplicación en modo plantillas. Los ejemplos se calcularon con la fecha del 7 de octubre de 2026.</p>
  <nav class="indice-manual" aria-label="Técnicas de este capítulo">
    <ol>
      @for(ManualPorFamilia.TecnicaManual tm : c.tecnicas())
        <li><a href="#tecnica-${tm.tecnica().id().valor()}">${tm.tecnica().cita()}</a></li>
      @endfor
    </ol>
  </nav>
  @for(ManualPorFamilia.TecnicaManual tm : c.tecnicas())
    !{String id = tm.tecnica().id().valor();}
    <section id="tecnica-${id}" class="manual-tecnica" aria-labelledby="titulo-${id}">
      <h2 id="titulo-${id}">${tm.tecnica().cita()}</h2>
      <p class="nombre-llano">${tm.tecnica().nombreLlano()}</p>
      <dl>
        <dt>Qué es</dt>
        <dd>${tm.tecnica().definicion()}</dd>
        <dt>Úsala cuando</dt>
        <dd>${tm.tecnica().usalaCuando()}</dd>
        <dt>Origen</dt>
        <dd>${tm.tecnica().origen()}</dd>
        <dt>Tipo</dt>
        <dd>${tm.tecnica().tipo().name().toLowerCase().replace('_', ' ')} · ${tm.tecnica().operacion().name().toLowerCase()} ·
          ${tm.tecnica().objeto().name().toLowerCase().replace('_', ' ')} · ${tm.tecnica().modalidad().name().toLowerCase()}</dd>
        <dt>IA</dt>
        <dd>${tm.ia()}</dd>
        <dt>Configuración de fábrica</dt>
        <dd>${tm.configuracion().isEmpty() ? "sin parámetros" : tm.configuracion()}</dd>
      </dl>
      @if(!tm.relacionadas().isEmpty())
        <h3 class="subtitulo-resultado">Relacionadas</h3>
        <ul class="lista-aparte">
          @for(String r : tm.relacionadas())
            <li>${r}</li>
          @endfor
        </ul>
      @endif
      <h3 class="subtitulo-resultado">Ejemplos (${tm.ejemplos().size()})</h3>
      @for(ManualPorFamilia.EjemploManual e : tm.ejemplos())
        <article class="manual-ejemplo tarjeta" data-ejemplo="${id}-${e.orden()}">
          <h4>Ejemplo ${e.orden()} · ${e.ambito()}: ${e.titulo()}</h4>
          <p><strong>Datos ficticios.</strong> ${e.nota()}</p>
          <p><strong>Configuración usada.</strong> ${e.configuracion().isEmpty() ? "la de fábrica" : e.configuracion()}</p>
          ${e.resultado()}
        </article>
      @endfor
    </section>
  @endfor
</article>
