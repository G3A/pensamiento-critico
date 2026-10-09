@import pensamiento.nucleo.Propuesta
@param String afirmacion
@param String fragmento
@param Propuesta propuesta
<div class="propuesta propuesta-sin-adoptar" role="status" data-propuesta="${propuesta.valor()}">
  <p><span class="chip chip-pendiente">propuesta del modelo · sin adoptar · no cuenta</span> <strong>${propuesta.valor()}</strong>
    @if(!propuesta.porque().isBlank())<span class="detalle">Por qué, según el modelo: ${propuesta.porque()}</span>@endif</p>
  <p class="aviso-experimental"><span class="chip chip-aviso">experimental</span> <span class="detalle">En la evaluación del hito 6 el modelo obedeció una instrucción escondida en un documento: lee el pasaje antes de adoptar.</span></p>
    @if(propuesta.valor().equals("irrelevante"))
    <p class="detalle">El modelo lo ve irrelevante para la afirmación: no hace falta usarlo. Si no estás de acuerdo, úsalo y elige tú la postura.</p>
  @else
    <p><a class="boton boton-chico" href="/verificar/${afirmacion}/fuentes/nueva?fragmento=${fragmento}&amp;postura=${propuesta.valor()}&amp;propuesta=${propuesta.valor()}&amp;modelo=true&amp;porque=${java.net.URLEncoder.encode(propuesta.porque(), java.nio.charset.StandardCharsets.UTF_8)}">Adoptar y usar como evidencia</a></p>
  @endif
</div>
