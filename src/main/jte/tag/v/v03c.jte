@import pensamiento.web.patrones.V03c
@param V03c v
<section id="${v.idRaiz()}" data-patron="V03c" data-modo="${v.modo().name().toLowerCase()}" class="resultado v03c" aria-labelledby="${v.idRaiz()}-titulo">
  <h3 id="${v.idRaiz()}-titulo" class="titulo-resultado">${v.titulo()}</h3>
  @if(v.enunciado() != null)
    <p class="enunciado">${v.enunciado()}</p>
  @endif
  @if(v.nota() != null)
    <p class="detalle">${v.nota()}</p>
  @endif
  <div class="rejilla">
    @for(V03c.Celda c : v.celdas())
      <section class="celda" data-celda="${c.clave()}" aria-labelledby="${v.idRaiz()}-celda-${c.clave()}">
        <h4 id="${v.idRaiz()}-celda-${c.clave()}"><span class="letra">${c.letra()}</span> ${c.nombre()} <span class="cuenta">· ${c.textos().size()}</span></h4>
        @if(c.estado() != null)
          <p><span class="chip chip-aviso">${c.estado()}</span></p>
        @endif
        <ul>
          @for(V03c.Texto t : c.textos())
            <li>@if(t.seleccionado())<span class="chip chip-ok">seleccionada</span> @endif${t.texto()}</li>
          @endfor
        </ul>
      </section>
    @endfor
  </div>
  @if(!v.seleccion().isEmpty())
    <h4 class="subtitulo-resultado">${v.tituloSeleccion()}</h4>
    <ul class="seleccion">
      @for(String s : v.seleccion())
        <li>${s}</li>
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
