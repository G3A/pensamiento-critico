@import pensamiento.web.patrones.V04
@param V04 v
<section id="${v.idRaiz()}" data-patron="V04" data-modo="${v.modo().name().toLowerCase()}" class="resultado v04" aria-labelledby="${v.idRaiz()}-titulo">
  <h3 id="${v.idRaiz()}-titulo" class="titulo-resultado">${v.titulo()}</h3>
  @if(v.enunciado() != null)
    <p class="enunciado">${v.enunciado()}</p>
  @endif
  <div class="dos-columnas">
    @for(V04.Columna col : java.util.List.of(v.izquierda(), v.derecha()))
      <section class="columna" aria-label="${col.titulo()}">
        <h4>${col.titulo()}</h4>
        @if(col.items().isEmpty())
          <p class="detalle vacia">${col.vacia()}</p>
        @else
          <ul class="items-columna">
            @for(V04.Item it : col.items())
              <li>
                @if(it.chip() != null)<span class="${it.claseChip()}">${it.chip()}</span> @endif${it.texto()}
                @if(it.detalle() != null)
                  <p class="detalle">${it.detalle()}</p>
                @endif
              </li>
            @endfor
          </ul>
        @endif
      </section>
    @endfor
  </div>
  @if(v.estado() != null)
    <p class="estado-resultado"><span class="detalle">${v.estado().etiqueta()}</span> <span class="${v.estado().claseChip()}">${v.estado().texto()}</span>
      @if(v.estado().motivo() != null)<span class="motivo">${v.estado().motivo()}</span>@endif</p>
  @endif
  @if(!v.preguntas().isEmpty())
    <h4 class="subtitulo-resultado">${v.tituloPreguntas()}</h4>
    <ol class="preguntas-abiertas">
      @for(String p : v.preguntas())
        <li>${p}</li>
      @endfor
    </ol>
  @endif
  @template.tag.v.propuestas(propuestas = v.propuestas(), idRaiz = v.idRaiz())
  <div class="tarjeta-resultado" role="status">
    <p class="titular">${v.resumen()}</p>
    <p>${v.tarjeta()}</p>
  </div>
  @if(v.lectura() && v.idEjecucion().isPresent())
    <p class="acciones-lectura"><a href="/ejecuciones/${v.idEjecucion().get().toString()}">Abrir en la técnica</a></p>
  @endif
</section>
