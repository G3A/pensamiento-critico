@import pensamiento.web.Pagina
@import gg.jte.Content
@import pensamiento.nucleo.Tecnica
@import pensamiento.nucleo.Ejecucion
@import pensamiento.nucleo.Expediente
@import java.util.List
@param Pagina pagina
@param Tecnica tecnica
@param Ejecucion ejecucion
@param String fecha
@param Expediente expediente
@param List<Expediente> expedientes
@param Content resultado
@template.layout(pagina = pagina, contenido = @`
  <section class="ejecucion" id="ejecucion-${ejecucion.id().toString()}" aria-labelledby="ejecucion-titulo">
    <p class="familia-ficha"><a href="/tecnicas/${tecnica.id().valor()}">${tecnica.cita()}</a></p>
    <h1 id="ejecucion-titulo">${ejecucion.resumen()}</h1>
    <p class="detalle">Guardada el ${fecha}.
      @if(expediente != null) En el expediente <a href="/expedientes/${expediente.id().toString()}">${expediente.nombre()}</a>.@else Sin expediente.@endif
    </p>
    ${resultado}
    @template.fragmentos.asociar(ejecucion = ejecucion, expedientes = expedientes, mensaje = "")
    <p class="acciones">
      <a class="boton boton-secundario" href="/tecnicas/${tecnica.id().valor()}?reejecutar=${ejecucion.id().toString()}">Reejecutar con la configuración actual</a>
      <a class="boton boton-secundario" href="/tecnicas/${tecnica.id().valor()}?pestana=historial">Ver el historial</a>
    </p>
  </section>
`)
