@import pensamiento.web.Pagina
@import pensamiento.nucleo.Expediente
@param Pagina pagina
@param Expediente expediente
@template.layout(pagina = pagina, contenido = @`
  <section class="expediente" id="expediente-${expediente.id().toString()}">
    <h1>${expediente.nombre()}</h1>
    <p class="chips"><span class="chip">${expediente.estado().name().toLowerCase()}</span> <span class="chip">0 ejecuciones</span></p>
    <p class="detalle">La línea de tiempo, el resumen por familia y "qué falta para cerrar" llegan en el hito 1.</p>
  </section>
`)
