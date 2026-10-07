@import pensamiento.nucleo.Tecnica
@import java.util.List
@param List<Tecnica> tecnicas
@param String familiaActiva
@param String intencionActiva
<div class="rejilla-encabezado">
  @if(!intencionActiva.isEmpty())
    <p class="detalle">Filtrado por intención: <strong>${intencionActiva}</strong> · <a href="/catalogo">quitar filtro</a></p>
  @endif
  <p class="detalle">${tecnicas.size()} técnicas en pantalla. Todas están <span class="chip chip-pendiente">pendientes</span>: la ficha "Qué es" ya existe; la pestaña Usar llega hito a hito.</p>
</div>
<ul class="rejilla" aria-label="Técnicas">
  @for(Tecnica t : tecnicas)
    <li class="tarjeta tecnica" id="tecnica-${t.id().valor()}" data-familia="${t.familia()}">
      <h2 class="nombre-llano">${t.nombreLlano()}</h2>
      <p class="cita">${t.id().valor()} · ${t.nombre()}</p>
      <p class="chips">
        <span class="chip">${t.familia()}</span>
        <span class="chip">${t.modalidad().name().toLowerCase()}</span>
        @if(t.requiereIa() == Tecnica.RequiereIa.NO)
          <span class="chip chip-ok">sin IA</span>
        @elseif(t.requiereIa() == Tecnica.RequiereIa.OPCIONAL)
          <span class="chip">Ollama opcional</span>
        @else
          <span class="chip chip-aviso">necesita Ollama</span>
        @endif
        <span class="chip chip-pendiente">pendiente</span>
      </p>
      <p class="definicion">${t.definicion()}</p>
      <p class="usala"><strong>Úsala cuando</strong> ${t.usalaCuando()}</p>
      <p class="origen">Origen: ${t.origen()}</p>
    </li>
  @endfor
</ul>
