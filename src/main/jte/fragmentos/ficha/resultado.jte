@import gg.jte.Content
@import pensamiento.nucleo.Tecnica
@import pensamiento.nucleo.Ejecucion
@import pensamiento.nucleo.Expediente
@import java.util.List
@param Content resultado
@param Tecnica tecnica
@param Ejecucion ejecucion
@param List<Expediente> expedientes
@param String nuevaClave
${resultado}
@if(ejecucion != null)
  <p class="guardado" role="status"><span class="chip chip-ok">guardado</span> Quedó en tu historial.
    <a href="/ejecuciones/${ejecucion.id().toString()}">Abrir</a> ·
    <a href="/tecnicas/${tecnica.id().valor()}?pestana=historial">Ver el historial</a></p>
  @template.fragmentos.asociar(ejecucion = ejecucion, expedientes = expedientes, mensaje = "")
  <input type="hidden" id="clave-${tecnica.id().valor()}" name="_clave" value="${nuevaClave}" hx-swap-oob="true">
@else
  <p class="detalle sin-guardar"><span class="chip">sin guardar</span> Usa "Guardar en historial" para conservarlo y asociarlo a un expediente.</p>
@endif
