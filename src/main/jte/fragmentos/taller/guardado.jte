@import pensamiento.web.taller.VistaGuardado
@import pensamiento.nucleo.Ejecucion
@import pensamiento.nucleo.Expediente
@param VistaGuardado g
<div class="guardado-taller" role="status">
  <p><span class="chip chip-ok">guardado</span> Quedaron en tu historial ${g.ejecuciones().size()} ejecuciones:</p>
  <ul class="ejecuciones-taller">
    @for(int i = 0; i < g.ejecuciones().size(); i++)
      <li><a href="/ejecuciones/${g.ejecuciones().get(i).id().toString()}" data-ejecucion="${g.ejecuciones().get(i).id().toString()}">${g.citas().get(i)}</a>:
        ${g.ejecuciones().get(i).resumen()}</li>
    @endfor
  </ul>
</div>
<form id="taller-asociar" class="asociar" hx-post="/taller/expediente" hx-target="#taller-guardado" hx-swap="innerHTML" novalidate>
  @for(Ejecucion e : g.ejecuciones())
    <input type="hidden" name="ejecucion" value="${e.id().toString()}">
  @endfor
  <fieldset>
    <legend>Asociar las ${g.ejecuciones().size()} a un expediente</legend>
    <label for="taller-asociar-expediente">Expediente</label>
    <select id="taller-asociar-expediente" name="expediente">
      <option value="">Ninguno</option>
      @for(Expediente x : g.expedientes())
        <option value="${x.id().toString()}" selected="${x.id().toString().equals(g.expedienteActual())}">${x.nombre()}</option>
      @endfor
    </select>
    <label for="taller-asociar-nuevo">o crea uno nuevo con este nombre</label>
    <input type="text" id="taller-asociar-nuevo" name="nuevo" maxlength="120" aria-describedby="taller-asociar-estado">
    <button type="submit" class="boton boton-secundario" hx-disabled-elt="this">Asociar</button>
  </fieldset>
  <p id="taller-asociar-estado" class="detalle" role="status">${g.mensaje()}</p>
</form>
@if(g.nuevaClave() != null)
  <input type="hidden" id="clave-taller" name="_clave" value="${g.nuevaClave()}" hx-swap-oob="true">
@endif
