@import pensamiento.web.patrones.V10
@param V10 v
<section id="${v.idRaiz()}" data-patron="V10" data-modo="${v.modo().name().toLowerCase()}" class="resultado v10" aria-labelledby="${v.idRaiz()}-titulo">
  <h3 id="${v.idRaiz()}-titulo" class="titulo-resultado">${v.titulo()}</h3>
  @if(v.enunciado() != null)
    <p class="enunciado">${v.enunciado()}</p>
  @endif
  @for(V10.Barra b : v.barras())
    <p class="barra-puntaje" data-barra="${b.clave()}">
      <label for="${v.idBarra(b)}">${b.etiqueta()}</label>
      <meter id="${v.idBarra(b)}" min="${b.minimo()}" max="${b.maximo()}" value="${b.valor()}">${b.etiqueta()}</meter>
    </p>
  @endfor
  @if(!v.items().isEmpty())
    <h4 class="subtitulo-resultado">${v.tituloItems()}</h4>
    <ul class="items-veredicto">
      @for(V10.Item it : v.items())
        <li data-item="${it.clave()}">
          <span class="${it.claseChip()}">${it.chip()}</span> <strong>${it.nombre()}</strong>
          @if(it.texto() != null)<span class="texto-item">${it.texto()}</span>@endif
          @if(it.detalle() != null)
            <p class="detalle">${it.detalle()}</p>
          @endif
        </li>
      @endfor
    </ul>
  @endif
  @if(v.veredicto() != null)
    <p class="estado-resultado veredicto"><span class="detalle">${v.veredicto().etiqueta()}</span>
      <span class="${v.veredicto().claseChip()}">${v.veredicto().texto()}</span>
      @if(v.veredicto().motivo() != null)<span class="motivo">${v.veredicto().motivo()}</span>@endif</p>
  @endif
  @for(String linea : v.lineas())
    <p class="linea-resultado">${linea}</p>
  @endfor
  @template.tag.v.propuestas(propuestas = v.propuestas(), idRaiz = v.idRaiz())
  <div class="tarjeta-resultado" role="status">
    <p class="titular">${v.resumen()}</p>
    <p>${v.tarjeta()}</p>
  </div>
  @if(v.lectura() && v.idEjecucion().isPresent())
    <p class="acciones-lectura"><a href="/ejecuciones/${v.idEjecucion().get().toString()}">Abrir en la técnica</a></p>
  @endif
</section>
