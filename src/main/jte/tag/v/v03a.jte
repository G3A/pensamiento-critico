@import pensamiento.web.patrones.V03a
@import pensamiento.tecnicas.f5.ResultadoAch
@param V03a v
<section id="${v.idRaiz()}" data-patron="V03a" data-modo="${v.modo().name().toLowerCase()}" class="resultado v03a" aria-labelledby="${v.idRaiz()}-titulo">
  <h3 id="${v.idRaiz()}-titulo" class="titulo-resultado">Matriz de consistencia</h3>
  <p class="pregunta-resultado">${v.valor().pregunta()}</p>
  <ul class="leyenda-hipotesis" aria-label="Hipótesis">
    @for(ResultadoAch.HipotesisEvaluada h : v.valor().hipotesis())
      <li class="${v.esMenosRefutada(h.codigo()) ? "menos-refutada" : ""}"><strong>${h.codigo()}</strong> ${h.texto()}
        @if(v.esMenosRefutada(h.codigo()))<span class="chip chip-ok">menos refutada</span>@endif
      </li>
    @endfor
  </ul>
  <div class="tabla-desplazable" role="region" aria-label="Matriz de evidencias frente a hipótesis" tabindex="0">
    <table class="matriz">
      <caption>Cada evidencia frente a cada hipótesis. ${v.leyenda()}</caption>
      <thead>
        <tr>
          <th scope="col">Evidencia</th>
          @for(ResultadoAch.HipotesisEvaluada h : v.valor().hipotesis())
            <th scope="col"><abbr title="${h.texto()}">${h.codigo()}</abbr></th>
          @endfor
        </tr>
      </thead>
      <tbody>
        @for(ResultadoAch.EvidenciaEvaluada e : v.valor().evidencias())
          <tr>
            <th scope="row"><span class="codigo">${e.codigo()}</span> ${e.texto()} <span class="peso">· ${v.peso(e)}</span></th>
            @for(String celda : e.celdas())
              <td class="${v.clase(celda)}"><span class="marca">${v.marca(celda)}</span> <span class="palabra">${v.palabra(celda)}</span></td>
            @endfor
          </tr>
        @endfor
      </tbody>
      <tfoot>
        <tr>
          <th scope="row">Inconsistencias ponderadas</th>
          @for(ResultadoAch.HipotesisEvaluada h : v.valor().hipotesis())
            <td class="total ${v.esMenosRefutada(h.codigo()) ? "total-menor" : ""}">${h.inconsistencias()}</td>
          @endfor
        </tr>
      </tfoot>
    </table>
  </div>
  <div class="tarjeta-resultado" role="status">
    <p class="titular">${v.titular()}</p>
    @for(String frase : v.explicacion())
      <p>${frase}</p>
    @endfor
    <p class="subtitulo">Siguiente paso</p>
    <ul class="siguientes-pasos">
      @for(String paso : v.siguientesPasos())
        <li>${paso}</li>
      @endfor
    </ul>
  </div>
  @if(v.lectura() && v.idEjecucion().isPresent())
    <p class="acciones-lectura"><a href="/ejecuciones/${v.idEjecucion().get().toString()}">Abrir en la técnica</a></p>
  @endif
</section>
