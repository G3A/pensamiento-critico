@import pensamiento.web.patrones.V11
@param V11 v
<section id="${v.idRaiz()}" data-patron="V11" data-modo="${v.modo().name().toLowerCase()}" class="resultado v11 registro" aria-labelledby="${v.idRaiz()}-titulo">
  <h3 id="${v.idRaiz()}-titulo" class="titulo-resultado">${v.titulo()}</h3>
  @if(v.estado() != null)
    <p class="estado-resultado"><span class="detalle">Estado</span> <span class="${v.claseEstado()}">${v.estado()}</span></p>
  @endif
  @if(!v.campos().isEmpty())
    <dl>
      @for(V11.Campo c : v.campos())
        <dt>${c.nombre()}</dt>
        <dd>${c.valor()}</dd>
      @endfor
    </dl>
  @endif
  @if(v.grupos().isEmpty())
    <h4 class="subtitulo-resultado">Línea de tiempo</h4>
    <ol class="linea-tiempo">
      @for(V11.Hito h : v.linea())
        <li><time datetime="${h.fecha()}">${h.texto()}</time> · ${h.que()}</li>
      @endfor
    </ol>
  @else
    @for(V11.Grupo g : v.grupos())
      <h4 class="subtitulo-resultado">${g.titulo()}</h4>
      @if(g.resumen() != null)
        <p class="resumen-grupo">${g.resumen()}</p>
      @endif
      <ol class="linea-tiempo">
        @for(V11.Hito h : g.hitos())
          <li><time datetime="${h.fecha()}">${h.texto()}</time> · ${h.que()}</li>
        @endfor
      </ol>
    @endfor
  @endif
  @if(v.tituloBarras() != null)
    <h4 class="subtitulo-resultado">${v.tituloBarras()}</h4>
    @for(V11.Barra b : v.barras())
      <p class="completitud" data-barra="${b.clave()}">
        <label for="${v.idBarra(b)}">${b.etiqueta()}: ${b.valor()}</label>
        <meter id="${v.idBarra(b)}" min="0" max="${b.total()}" value="${b.valor()}">${b.valor()} de ${b.total()}</meter>
      </p>
    @endfor
  @endif
  @if(v.tituloAparte() != null)
    <h4 class="subtitulo-resultado">${v.tituloAparte()}</h4>
    @if(v.aparte().isEmpty())
      <p class="detalle">Ninguna.</p>
    @else
      <ul class="lista-aparte">
        @for(String a : v.aparte())
          <li>${a}</li>
        @endfor
      </ul>
    @endif
  @endif
  @if(v.bloqueo() != null)
    <p class="aviso-lista aviso-aviso" role="alert"><span class="chip chip-aviso">bloqueado</span> ${v.bloqueo()}</p>
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
