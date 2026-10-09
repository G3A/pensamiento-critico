@import pensamiento.web.patrones.V09
@param V09 v
<section id="${v.idRaiz()}" data-patron="V09" data-modo="${v.modo().name().toLowerCase()}" class="resultado v09" aria-labelledby="${v.idRaiz()}-titulo">
  <h3 id="${v.idRaiz()}-titulo" class="titulo-resultado">${v.titulo()}</h3>
  @if(v.enunciado() != null)
    <p class="enunciado">${v.enunciado()}</p>
  @endif
  <div class="transcripcion-con-panel">
    <div class="columna-transcripcion">
      <ol class="transcripcion" role="log" aria-label="Transcripción" id="${v.idRaiz()}-transcripcion">
        @for(V09.Burbuja b : v.transcripcion())
          <li class="burbuja burbuja-${b.rol()}" data-turno="${b.clave()}">
            <p class="autor"><strong>${b.autor()}</strong>
              @for(String chip : b.chips())
                <span class="chip">${chip}</span>
              @endfor
            </p>
            <p class="texto-burbuja">${b.texto()}</p>
            @if(b.detalle() != null)
              <p class="detalle">${b.detalle()}</p>
            @endif
          </li>
        @endfor
      </ol>
      @if(v.siguiente() != null)
        <div class="burbuja burbuja-${v.siguiente().rol()} burbuja-siguiente" data-turno="${v.siguiente().clave()}">
          <p class="autor"><strong>${v.siguiente().autor()}</strong>
            @for(String chip : v.siguiente().chips())
              <span class="chip chip-pendiente">${chip}</span>
            @endfor
          </p>
          <p class="texto-burbuja">${v.siguiente().texto()}</p>
          @if(v.siguiente().detalle() != null)
            <p class="detalle">${v.siguiente().detalle()}</p>
          @endif
        </div>
      @endif
    </div>
    <aside class="panel-lateral" aria-label="${v.tituloPanel()}">
      <h4 class="subtitulo-resultado">${v.tituloPanel()}</h4>
      @for(V09.SeccionPanel s : v.panel())
        @if(s.titulo() != null)
          <h5 class="titulo-panel">${s.titulo()}</h5>
        @endif
        <ul class="items-panel">
          @for(V09.ItemPanel it : s.items())
            <li data-item="${it.clave()}">
              <span class="${it.claseChip()}">${it.chip()}</span> <strong>${it.nombre()}</strong>
              @if(it.texto() != null)
                <span class="texto-parte">${it.texto()}</span>
              @endif
              @if(it.detalle() != null)
                <p class="detalle">${it.detalle()}</p>
              @endif
            </li>
          @endfor
        </ul>
      @endfor
    </aside>
  </div>
  @for(String aviso : v.avisos())
    <p class="aviso-lista aviso-nota"><span class="chip chip-aviso">aviso</span> ${aviso}</p>
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
