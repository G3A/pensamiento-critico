@import pensamiento.web.patrones.V01
@import pensamiento.tecnicas.f1.ResultadoMapa
@param V01 v
<section id="${v.idRaiz()}" data-patron="V01" data-modo="${v.modo().name().toLowerCase()}" class="resultado v01" aria-labelledby="${v.idRaiz()}-titulo">
  <h3 id="${v.idRaiz()}-titulo" class="titulo-resultado">Mapa del argumento</h3>
  <div class="${v.claseMapa()}" x-data="mapa" x-on:click="elegir">
    @if(v.svg().isPresent())
      <figure class="mapa-svg" role="group" aria-label="Mapa dibujado; la lista de abajo dice lo mismo con texto">
        $unsafe{v.svg().get()}
      </figure>
    @else
      <p class="detalle" role="status">No se pudo dibujar el mapa esta vez. La lista de abajo tiene las mismas afirmaciones y relaciones.</p>
    @endif
    <p class="leyenda-mapa detalle">Conclusión, premisa, objeción y premisa oculta se distinguen por el texto de cada nodo; las flechas punteadas atacan.</p>
    <h4 class="subtitulo">Afirmaciones</h4>
    <ol class="lista-nodos" aria-label="Afirmaciones del mapa">
      @for(ResultadoMapa.Nodo n : v.valor().nodos())
        <li class="nodo-${n.rol().toString()}">
          <button type="button" class="nodo-lista" data-nodo="${n.afirmacionId().toString()}" aria-pressed="false">
            <span class="codigo">${v.etiquetaNodo(n)}</span> ${n.texto()}
          </button>
        </li>
      @endfor
    </ol>
  </div>
  <h4 class="subtitulo">Argumentos bajo ${v.valor().estandar().nombre()}</h4>
  <ul class="lista-argumentos">
    @for(ResultadoMapa.ArgumentoMapa a : v.valor().argumentos())
      <li><span class="${v.claseArgumento(a)}">${v.estadoArgumento(a)}</span> <strong>${v.etiquetaArgumento(a)}</strong>. ${a.motivo()}</li>
    @endfor
  </ul>
  <ul class="lista-conclusiones">
    @for(ResultadoMapa.Aceptabilidad c : v.valor().conclusiones())
      <li><span class="${v.claseConclusion(c)}">${v.estadoConclusion(c)}</span> <strong>${c.conclusion()}</strong> ${v.textoConclusion(c)} ${c.frase()}</li>
    @endfor
  </ul>
  <div class="tarjeta-resultado" role="status">
    <p class="titular">${v.resumen()}</p>
    @for(String frase : v.tarjeta())
      <p>${frase}</p>
    @endfor
  </div>
  <details class="exportar-argdown">
    <summary>Texto Argdown para exportar</summary>
    <pre class="argdown">${v.valor().argdown()}</pre>
  </details>
  @if(v.lectura() && v.idEjecucion().isPresent())
    <p class="acciones-lectura"><a href="/ejecuciones/${v.idEjecucion().get().toString()}">Abrir en la técnica</a></p>
  @endif
</section>
