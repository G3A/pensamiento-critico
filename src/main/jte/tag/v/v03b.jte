@import pensamiento.web.patrones.V03b
@param V03b v
<section id="${v.idRaiz()}" data-patron="V03b" data-modo="${v.modo().name().toLowerCase()}" class="resultado v03b" aria-labelledby="${v.idRaiz()}-titulo">
  <h3 id="${v.idRaiz()}-titulo" class="titulo-resultado">${v.titulo()}</h3>
  @if(v.enunciado() != null)
    <p class="enunciado">${v.enunciado()}</p>
  @endif
  <div class="tabla-desplazable" tabindex="0" role="region" aria-label="Matriz: desplázala si no cabe">
    <table class="matriz-ponderada">
      <caption>Puntaje por criterio; el peso va entre paréntesis</caption>
      <thead>
        <tr>
          <th scope="col">Puesto</th>
          <th scope="col">${v.tituloOpcion()}</th>
          @for(V03b.Columna c : v.columnas())
            <th scope="col">${c.criterio()} (${c.peso()})</th>
          @endfor
          @if(v.conProbabilidad())<th scope="col">${v.tituloProbabilidad()}</th>@endif
          <th scope="col">${v.tituloTotal()}</th>
        </tr>
      </thead>
      <tbody>
        @for(V03b.Fila f : v.filas())
          <tr>
            <td>${f.puesto()}@if(f.empate()) <span class="chip chip-pendiente">empate</span>@endif</td>
            <th scope="row">${f.opcion()}</th>
            @for(Integer p : f.puntajes())
              <td>${p}</td>
            @endfor
            @if(v.conProbabilidad())<td>${f.probabilidad()}</td>@endif
            <td class="total">${f.total()}</td>
          </tr>
        @endfor
      </tbody>
    </table>
  </div>
  @if(v.ganador() != null)
    <p class="estado-resultado"><span class="chip chip-ok">1º</span> ${v.ganador()}
      @if(v.nivelSensibilidad() != null)<span class="chip">sensibilidad ${v.nivelSensibilidad()}</span>@endif</p>
  @endif
  @if(v.justificacion() != null)
    <p class="enunciado">${v.justificacion()}</p>
  @endif
  <h4 class="subtitulo-resultado">Sensibilidad a los pesos</h4>
  <ul class="sensibilidad">
    @for(String s : v.sensibilidad())
      <li>${s}</li>
    @endfor
  </ul>
  @for(String aviso : v.avisos())
    <p class="aviso-lista aviso-nota"><span class="chip chip-aviso">aviso</span> ${aviso}</p>
  @endfor
  <div class="tarjeta-resultado" role="status">
    <p class="titular">${v.resumen()}</p>
    <p>${v.tarjeta()}</p>
  </div>
  @if(v.lectura() && v.idEjecucion().isPresent())
    <p class="acciones-lectura"><a href="/ejecuciones/${v.idEjecucion().get().toString()}">Abrir en la técnica</a></p>
  @endif
</section>
