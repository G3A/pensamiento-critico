@import pensamiento.web.patrones.V05
@import pensamiento.tecnicas.f3.ResultadoFalacias
@param V05 v
<section id="${v.idRaiz()}" data-patron="V05" data-modo="${v.modo().name().toLowerCase()}" class="resultado v05" aria-labelledby="${v.idRaiz()}-titulo">
  <h3 id="${v.idRaiz()}-titulo" class="titulo-resultado">Tu texto, marcado</h3>
  <blockquote class="texto-marcado">
    @for(V05.Segmento s : v.segmentos())
      @if(s.marcado())<mark class="${s.clase()}"><span class="codigo-marca">${s.codigos()}</span> ${s.texto()}</mark>@else${s.texto()}@endif
    @endfor
  </blockquote>
  @if(!v.valor().marcas().isEmpty())
    <ol class="lista-marcas">
      @for(ResultadoFalacias.Marca m : v.valor().marcas())
        <li id="${v.idMarca(m)}" class="marca-falacia marca-${m.estado().toString()}" data-marca="${m.codigo()}">
          <p><strong>${m.codigo()}</strong> <span class="${v.claseEstado(m)}">${v.estado(m)}</span> · ${m.esquemaNombre()}</p>
          @if(v.valor().mostrarPregunta())
            <p class="pregunta-critica"><span class="detalle">Pregunta crítica sin responder:</span> ${m.preguntaTexto()}</p>
          @endif
          <p class="porque">${m.porque()}</p>
          <p class="como-responder"><span class="detalle">Para responderla:</span> ${m.comoResponder()}</p>
          <p class="etiqueta-falacia">${v.etiqueta(m)}</p>
        </li>
      @endfor
    </ol>
  @endif
  <div class="tarjeta-resultado" role="status">
    <p class="titular">${v.resumen()}</p>
    <p>${v.tarjeta()}</p>
  </div>
  @if(v.lectura() && v.idEjecucion().isPresent())
    <p class="acciones-lectura"><a href="/ejecuciones/${v.idEjecucion().get().toString()}">Abrir en la técnica</a></p>
  @endif
</section>
