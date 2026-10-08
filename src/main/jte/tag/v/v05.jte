@import pensamiento.web.patrones.V05
@param V05 v
<section id="${v.idRaiz()}" data-patron="V05" data-modo="${v.modo().name().toLowerCase()}" class="resultado v05" aria-labelledby="${v.idRaiz()}-titulo">
  <h3 id="${v.idRaiz()}-titulo" class="titulo-resultado">${v.titulo()}</h3>
  <blockquote class="texto-marcado">
    @for(V05.Segmento s : v.segmentos())
      @if(s.marcado())<mark class="${s.clase()}"><span class="codigo-marca">${s.codigos()}</span> ${s.texto()}</mark>@else${s.texto()}@endif
    @endfor
  </blockquote>
  @if(!v.notas().isEmpty())
    <ol class="lista-marcas ${v.claseNotas()}">
      @for(V05.Nota n : v.notas())
        <li id="${v.idNota(n)}" class="${n.claseItem()}" data-marca="${n.codigo()}">
          <p><strong>${n.codigo()}</strong> <span class="${n.claseChip()}">${n.chip()}</span> · ${n.titulo()}</p>
          @for(V05.Linea l : n.lineas())
            <p class="${l.clase()}">@if(l.etiqueta() != null)<span class="detalle">${l.etiqueta()}</span> @endif${l.texto()}</p>
          @endfor
        </li>
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
