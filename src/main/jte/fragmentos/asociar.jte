@import pensamiento.nucleo.Ejecucion
@import pensamiento.nucleo.Expediente
@import java.util.List
@param Ejecucion ejecucion
@param List<Expediente> expedientes
@param String mensaje
!{String id = ejecucion.id().toString();}
<form id="asociar-${id}" class="asociar" hx-post="/ejecuciones/${id}/expediente" hx-target="this" hx-swap="outerHTML" novalidate>
  <fieldset>
    <legend>Asociar a un expediente</legend>
    <label for="asociar-${id}-expediente">Expediente</label>
    <select id="asociar-${id}-expediente" name="expediente">
      <option value="">Ninguno</option>
      @for(Expediente x : expedientes)
        <option value="${x.id().toString()}" selected="${ejecucion.expedienteId().map(e -> e.equals(x.id())).orElse(false)}">${x.nombre()}</option>
      @endfor
    </select>
    <label for="asociar-${id}-nuevo">o crea uno nuevo con este nombre</label>
    <input type="text" id="asociar-${id}-nuevo" name="nuevo" maxlength="120" aria-describedby="asociar-${id}-estado">
    <button type="submit" class="boton boton-secundario" hx-disabled-elt="this">Asociar</button>
  </fieldset>
  <p id="asociar-${id}-estado" class="detalle" role="status">${mensaje}</p>
</form>
