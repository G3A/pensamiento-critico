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
  @elseif(!v.curva().isEmpty())
    <figure class="grafico-curva">
      <svg viewBox="0 0 ${v.bordeCurva() + 10} ${v.bordeCurva() + 24}" role="img" aria-labelledby="${v.idRaiz()}-grafico" xmlns="http://www.w3.org/2000/svg">
        <title id="${v.idRaiz()}-grafico">${v.tituloGrafico()}</title>
        <rect x="${V08.MARGEN_CURVA}" y="${V08.MARGEN_CURVA}" width="${V08.LADO_CURVA}" height="${V08.LADO_CURVA}" class="marco"></rect>
        <line x1="${V08.MARGEN_CURVA}" y1="${v.bordeCurva()}" x2="${v.bordeCurva()}" y2="${V08.MARGEN_CURVA}" class="diagonal"></line>
        <text x="${V08.MARGEN_CURVA}" y="${v.bordeCurva() + 12}" class="texto-eje">0</text>
        <text x="${V08.MARGEN_CURVA + V08.LADO_CURVA / 2}" y="${v.bordeCurva() + 12}" class="texto-eje">50%</text>
        <text x="${v.bordeCurva() - 18}" y="${v.bordeCurva() + 12}" class="texto-eje">100%</text>
        <text x="${V08.MARGEN_CURVA}" y="${v.bordeCurva() + 22}" class="texto-eje">confianza declarada</text>
        <text x="2" y="${V08.MARGEN_CURVA + 8}" class="texto-eje">100%</text>
        @if(v.curva().size() > 1)
          <polyline points="${v.puntosCurva()}" class="linea-curva"></polyline>
        @endif
        @for(V08.Punto p : v.curva())
          <circle cx="${p.x()}" cy="${p.y()}" r="4" class="punto${p.provisional() ? " provisional" : ""}"></circle>
        @endfor
      </svg>
      <figcaption class="frase-grafico">${v.frase()}</figcaption>
    </figure>
    <table class="tabla-frecuencias">
      <caption>La curva con números: lo que declaras contra lo que se cumple</caption>
      <thead><tr><th scope="col">Tramo</th><th scope="col">Resueltas</th><th scope="col">Declaras</th><th scope="col">Se cumple</th></tr></thead>
      <tbody>
        @for(V08.FilaCurva f : v.tablaCurva())
          <tr><th scope="row">${f.tramo()}</th><td>${f.n()}@if(f.provisional()) <span class="chip chip-pendiente">provisional</span>@endif</td>
            <td>${f.declarada()}%</td><td>${f.cumplida()}%</td></tr>
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
