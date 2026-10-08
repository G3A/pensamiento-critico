@import pensamiento.nucleo.Propuesta
@import java.util.List
@param List<Propuesta> propuestas
@param String idRaiz
@if(!propuestas.isEmpty())
  <section class="propuestas-resultado" aria-labelledby="${idRaiz}-propuestas">
    <h4 id="${idRaiz}-propuestas" class="subtitulo-resultado">Propuestas del modelo</h4>
    <ul>
      @for(Propuesta p : propuestas)
        <li data-propuesta="${p.codigo()}" class="${p.adoptada() ? "propuesta-adoptada" : "propuesta-sin-adoptar"}">
          <strong>${p.codigo()}</strong>
          @if(p.adoptada())
            <span class="chip chip-ok">adoptada por ti · cuenta</span>
          @else
            <span class="chip chip-pendiente">propuesta del modelo · sin adoptar · no cuenta</span>
          @endif
          ${p.rotulo()}: ${p.valor()}
          @if(!p.porque().isBlank())
            <span class="detalle">Por qué, según el modelo: ${p.porque()}</span>
          @endif
        </li>
      @endfor
    </ul>
  </section>
@endif
