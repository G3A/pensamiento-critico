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
!{String id = tecnica.id().valor();}
@if(ejecucion != null)
  <p class="guardado" role="status"><span class="chip chip-ok">guardado</span> Quedó en tu historial.
    <a href="/ejecuciones/${ejecucion.id().toString()}">Abrir</a> ·
    <a href="/tecnicas/${id}?pestana=historial">Ver el historial</a></p>
@endif
${resultado}
@if(ejecucion != null)
  @template.fragmentos.asociar(ejecucion = ejecucion, expedientes = expedientes, mensaje = "")
  <input type="hidden" id="clave-${id}" name="_clave" value="${nuevaClave}" hx-swap-oob="true">
  <p id="estado-${id}" class="estado-guardado" role="status" hx-swap-oob="true"><span class="chip chip-ok">guardado</span> Quedó en tu historial.
    <a href="/ejecuciones/${ejecucion.id().toString()}">Abrir</a></p>
@else
  <p class="detalle sin-guardar"><span class="chip">sin guardar</span> Usa "Guardar en historial" para conservarlo y asociarlo a un expediente.</p>
  <p id="estado-${id}" class="estado-guardado" role="status" hx-swap-oob="true"></p>
@endif
