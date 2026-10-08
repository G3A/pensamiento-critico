@import pensamiento.web.patrones.V06
@param V06 v
<section id="${v.idRaiz()}" data-patron="V06" data-modo="${v.modo().name().toLowerCase()}" class="resultado v06" aria-labelledby="${v.idRaiz()}-titulo">
  <h3 id="${v.idRaiz()}-titulo" class="titulo-resultado">${v.titulo()}</h3>
  @if(v.enunciado() != null)
    <p class="enunciado">${v.enunciado()}</p>
  @endif
  <div class="diagrama">
    @if(v.svg().isPresent())
      <figure class="diagrama-svg" role="group" aria-label="${v.descripcionSvg()}">
        $unsafe{v.svg().get()}
      </figure>
    @else
      <p class="detalle" role="status">No se pudo dibujar el diagrama esta vez. La lista de abajo tiene lo mismo con texto.</p>
    @endif
  </div>
  <h4 class="subtitulo-resultado">${v.tituloLista()}</h4>
  <ol class="lista-arbol" aria-label="${v.tituloLista()}">
    @for(V06.Item it : v.items())
      <li class="nivel-${it.nivel()}" data-nodo="${it.id()}">
        <span class="codigo">${it.codigo()}</span> ${it.texto()}
        @if(it.chip() != null)<span class="${it.claseChip()}">${it.chip()}</span>@endif
      </li>
    @endfor
  </ol>
  @for(V06.Seccion s : v.secciones())
    @if(!s.lineas().isEmpty())
      <h4 class="subtitulo-resultado">${s.titulo()}</h4>
      <ul class="lineas-seccion">
        @for(String linea : s.lineas())
          <li>${linea}</li>
        @endfor
      </ul>
    @endif
  @endfor
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
