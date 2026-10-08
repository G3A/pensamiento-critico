@import pensamiento.web.patrones.V13a
@param V13a v
<section id="${v.idRaiz()}" data-patron="V13a" data-modo="${v.modo().name().toLowerCase()}" class="resultado v13a" aria-labelledby="${v.idRaiz()}-titulo">
  <h3 id="${v.idRaiz()}-titulo" class="titulo-resultado">${v.titulo()}</h3>
  @if(v.enunciado() != null)
    <p class="enunciado">${v.enunciado()}</p>
  @endif
  <ol class="lista-priorizada">
    @for(V13a.Item it : v.items())
      <li data-item="${it.clave()}">
        <span class="${it.claseChip()}">${it.chip()}</span> <strong>${it.nombre()}</strong>
        <p class="detalle">${it.detalle()}</p>
      </li>
    @endfor
  </ol>
  @if(!v.acciones().isEmpty())
    <h4 class="subtitulo-resultado">${v.tituloAcciones()}</h4>
    <ul class="acciones-sugeridas">
      @for(String a : v.acciones())
        <li>${a}</li>
      @endfor
    </ul>
  @endif
  <div class="tarjeta-resultado" role="status">
    <p class="titular">${v.resumen()}</p>
    <p>${v.tarjeta()}</p>
  </div>
  @if(v.lectura() && v.idEjecucion().isPresent())
    <p class="acciones-lectura"><a href="/ejecuciones/${v.idEjecucion().get().toString()}">Abrir en la técnica</a></p>
  @endif
</section>
