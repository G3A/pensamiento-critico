@import pensamiento.web.patrones.V12
@param V12 v
<section id="${v.idRaiz()}" data-patron="V12" data-modo="${v.modo().name().toLowerCase()}" class="resultado v12" aria-labelledby="${v.idRaiz()}-titulo">
  <h3 id="${v.idRaiz()}-titulo" class="titulo-resultado">${v.titulo()}</h3>
  @if(v.enunciado() != null)
    <p class="enunciado">${v.enunciado()}</p>
  @endif
  <figure class="grafico-ranking">
    <svg viewBox="0 0 ${V12.X_CERO + V12.MEDIO + 50} ${v.altoSvg()}" role="img" aria-labelledby="${v.idRaiz()}-grafico" xmlns="http://www.w3.org/2000/svg">
      <title id="${v.idRaiz()}-grafico">Valor de cada opción; las barras a la izquierda del cero son negativas</title>
      @for(int i = 0; i < v.filas().size(); i++)
        !{V12.Fila f = v.filas().get(i);}
        <g class="fila-barra" data-puesto="${f.puesto()}">
          <text x="0" y="${v.y(i) + 16}" class="etiqueta-barra">${f.puesto()}º ${f.opcion()}</text>
          <rect x="${f.x()}" y="${v.y(i) + 2}" width="${f.ancho()}" height="18" class="barra ${f.negativa() ? "barra-negativa" : "barra-positiva"}"></rect>
          <text x="${V12.X_CERO + V12.MEDIO + 4}" y="${v.y(i) + 16}" class="valor-barra">${f.valor()}</text>
        </g>
      @endfor
    </svg>
  </figure>
  @if(v.nota() != null)
    <p class="detalle">${v.nota()}</p>
  @endif
  <ol class="ranking" aria-label="Ranking de opciones">
    @for(V12.Fila f : v.filas())
      <li data-puesto="${f.puesto()}"><strong>${f.puesto()}º ${f.opcion()}</strong> ${f.valor()}
        @if(f.chip() != null)<span class="chip chip-pendiente">${f.chip()}</span>@endif
        <span class="detalle">· ${f.detalle()}</span></li>
    @endfor
  </ol>
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
