@import pensamiento.web.patrones.V08
@param V08 v
<section id="${v.idRaiz()}" data-patron="V08" data-modo="${v.modo().name().toLowerCase()}" class="resultado v08" aria-labelledby="${v.idRaiz()}-titulo">
  <h3 id="${v.idRaiz()}-titulo" class="titulo-resultado">${v.titulo()}</h3>
  @if(v.enunciado() != null)
    <p class="enunciado">${v.enunciado()}</p>
  @endif
  @if(!v.barras().isEmpty())
    <figure class="grafico-frecuencias">
      <svg viewBox="0 0 ${V08.X_BARRAS + V08.ANCHO_BARRAS + 50} ${v.altoSvg()}" role="img" aria-labelledby="${v.idRaiz()}-grafico ${v.idRaiz()}-frase"
           xmlns="http://www.w3.org/2000/svg">
        <title id="${v.idRaiz()}-grafico">${v.tituloGrafico()}</title>
        @for(int i = 0; i < v.barras().size(); i++)
          !{V08.Barra b = v.barras().get(i);}
          <g class="fila-barra" data-barra="${b.clave()}">
            <text x="0" y="${v.y(i) + 16}" class="etiqueta-barra">${b.etiqueta()}</text>
            <rect x="${V08.X_BARRAS}" y="${v.y(i) + 2}" width="${b.ancho()}" height="18" class="barra ${b.clase()}"></rect>
            <text x="${V08.X_BARRAS + b.ancho() + 6}" y="${v.y(i) + 16}" class="valor-barra">${b.valor()}</text>
          </g>
        @endfor
      </svg>
      <figcaption id="${v.idRaiz()}-frase" class="frase-grafico">${v.frase()}</figcaption>
    </figure>
    <table class="tabla-frecuencias">
      <caption>Los números del cálculo</caption>
      <tbody>
        @for(V08.Fila f : v.filasTabla())
          <tr><th scope="row">${f.etiqueta()}</th><td>${f.valor()}</td></tr>
        @endfor
      </tbody>
    </table>
  @elseif(v.frase() != null)
    <p class="frase-grafico aviso-ia"><span class="chip chip-pendiente">sin tasa base</span> ${v.frase()}</p>
  @endif
  @if(!v.items().isEmpty())
    <h4 class="subtitulo-resultado">${v.tituloItems()}</h4>
    <ul class="items-veredicto">
      @for(V08.Item it : v.items())
        <li data-item="${it.clave()}"><span class="${it.claseChip()}">${it.chip()}</span> <strong>${it.nombre()}</strong>
          <span class="detalle">· ${it.detalle()}</span></li>
      @endfor
    </ul>
  @endif
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
