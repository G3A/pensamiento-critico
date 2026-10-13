@import pensamiento.web.patrones.V03a
@import pensamiento.tecnicas.f5.ResultadoAch
@param V03a v
<section id="${v.idRaiz()}" data-patron="V03a" data-modo="${v.modo().name().toLowerCase()}" class="resultado v03a" aria-labelledby="${v.idRaiz()}-titulo">
  <h3 id="${v.idRaiz()}-titulo" class="titulo-resultado">Qué dicen tus pistas</h3>
  <p class="pregunta-resultado">${v.valor().pregunta()}</p>
  <div class="tarjeta-resultado" role="status">
    <p class="titular">${v.titular()}</p>
    @for(String frase : v.explicacion())
      <p>${frase}</p>
    @endfor
    <p class="subtitulo">Pistas en contra de cada idea</p>
    <ul class="ideas-en-contra">
      @for(V03a.Idea idea : v.ideas())
        <li class="${idea.enPie() ? "en-pie" : ""}" data-idea="${idea.codigo()}">
          <span class="idea-texto"><strong>${idea.codigo()}</strong> ${idea.texto()}
            <span class="chip ${idea.enPie() ? "chip-pendiente" : "chip-aviso"}">${idea.lectura()}</span></span>
          <meter min="0" max="${idea.maximo()}" value="${idea.peso()}" aria-label="Peso en contra de ${idea.codigo()}">${idea.peso()}</meter>
          <span class="cuenta">${idea.cuenta()}</span>
        </li>
      @endfor
    </ul>
    <p class="subtitulo">Qué hacer ahora</p>
    <ul class="siguientes-pasos">
      @for(String paso : v.siguientesPasos())
        <li>${paso}</li>
      @endfor
    </ul>
  </div>
  <details class="tabla-completa">
    <summary>Ver la tabla completa</summary>
    <div class="tabla-desplazable" role="region" aria-label="Tabla de pistas frente a ideas" tabindex="0">
      <table class="matriz">
        <caption>${v.leyenda()}</caption>
        <thead>
          <tr>
            <th scope="col">Pista</th>
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
                <td class="${v.clase(celda)}">@if(v.escalaNumerica())<span class="marca">${v.marca(celda)}</span> @endif<span class="palabra">${v.palabra(celda)}</span></td>
              @endfor
            </tr>
          @endfor
        </tbody>
        <tfoot>
          <tr>
            <th scope="row">Peso de lo que va en contra</th>
            @for(ResultadoAch.HipotesisEvaluada h : v.valor().hipotesis())
              <td class="total ${v.esMenosRefutada(h.codigo()) ? "total-menor" : ""}">${h.inconsistencias()}</td>
            @endfor
          </tr>
        </tfoot>
      </table>
    </div>
  </details>
  @if(v.lectura() && v.idEjecucion().isPresent())
    <p class="acciones-lectura"><a href="/ejecuciones/${v.idEjecucion().get().toString()}">Abrir en la técnica</a></p>
  @endif
</section>
