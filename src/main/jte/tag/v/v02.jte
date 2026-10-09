@import pensamiento.web.patrones.V02
@param V02 v
<section id="${v.idRaiz()}" data-patron="V02" data-modo="${v.modo().name().toLowerCase()}" class="resultado v02" aria-labelledby="${v.idRaiz()}-titulo">
  <h3 id="${v.idRaiz()}-titulo" class="titulo-resultado">${v.titulo()}</h3>
  @if(v.enunciado() != null)
    <p class="enunciado">${v.enunciado()}</p>
  @endif
  @for(V02.Medidor m : v.medidores())
    <p class="completitud" data-medidor="${m.clave()}">
      <label for="${v.idMedidor(m)}">${m.etiqueta()}</label>
      <meter id="${v.idMedidor(m)}" min="0" max="${m.total()}" value="${m.valor()}">${m.valor()} de ${m.total()}</meter>
    </p>
  @endfor
  @for(V02.Seccion s : v.secciones())
    @if(s.titulo() != null)
      <h4 class="subtitulo-resultado">${s.titulo()}</h4>
    @endif
    <ol class="lista-verificacion">
      @for(V02.Item it : s.items())
        <li class="parte parte-${it.claseItem()}" data-parte="${it.clave()}">
          <span class="${it.claseChip()}">${it.estado()}</span>
          <strong>${it.nombre()}</strong>
          @if(it.texto() != null)
            <span class="texto-parte">${it.texto()}</span>
          @endif
          @if(it.detalle() != null && !it.detalle().isEmpty())
            <span class="detalle">· ${it.detalle()}</span>
          @endif
          @if(it.falta() != null)
            <p class="falta">${it.falta()}</p>
          @endif
        </li>
      @endfor
    </ol>
  @endfor
  @for(V02.Aviso a : v.avisos())
    <p class="aviso-lista aviso-${a.clase()}">@if(a.clase().equals("aviso"))<span class="chip chip-aviso">bloqueado</span> @elseif(a.clase().equals("alerta"))<span class="chip chip-aviso">alerta</span> @endif${a.texto()}</p>
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
