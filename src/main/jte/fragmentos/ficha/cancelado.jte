@param String tecnica
@param String idPedir
<p class="aviso-ia" role="status"><span class="chip">cancelado</span> Cancelaste el pedido: sigues con lo que tenías, en modo plantillas.</p>
<div id="${idPedir}" class="pedir-modelo" hx-swap-oob="true">
  <button type="button" class="boton boton-secundario" hx-post="/tecnicas/${tecnica}/propuestas" hx-target="#espera-${tecnica}"
          hx-swap="innerHTML" hx-disabled-elt="this" hx-sync="closest form:drop" data-accion="proponer">Pedir propuestas al modelo</button>
  <span class="detalle">Lo que proponga no cuenta hasta que lo adoptes.</span>
</div>
