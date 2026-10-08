@import pensamiento.web.patrones.V11
@param V11 v
<section id="${v.idRaiz()}" data-patron="V11" data-modo="${v.modo().name().toLowerCase()}" class="resultado v11 registro" aria-labelledby="${v.idRaiz()}-titulo">
  <h3 id="${v.idRaiz()}-titulo" class="titulo-resultado">${v.titulo()}</h3>
  <p class="estado-resultado"><span class="detalle">Estado</span> <span class="${v.claseEstado()}">${v.estado()}</span></p>
  <dl>
    @for(V11.Campo c : v.campos())
      <dt>${c.nombre()}</dt>
      <dd>${c.valor()}</dd>
    @endfor
  </dl>
  <h4 class="subtitulo-resultado">Línea de tiempo</h4>
  <ol class="linea-tiempo">
    @for(V11.Hito h : v.linea())
      <li><time datetime="${h.fecha()}">${h.texto()}</time> · ${h.que()}</li>
    @endfor
  </ol>
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
