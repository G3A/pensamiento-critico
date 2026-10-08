@import pensamiento.web.patrones.V02
@import pensamiento.tecnicas.f1.ResultadoToulmin
@param V02 v
<section id="${v.idRaiz()}" data-patron="V02" data-modo="${v.modo().name().toLowerCase()}" class="resultado v02" aria-labelledby="${v.idRaiz()}-titulo">
  <h3 id="${v.idRaiz()}-titulo" class="titulo-resultado">Las partes del argumento</h3>
  <p class="completitud">
    <label for="${v.idRaiz()}-medidor">Completitud ${v.valor().completas()} de ${v.valor().total()}</label>
    <meter id="${v.idRaiz()}-medidor" min="0" max="${v.valor().total()}" value="${v.valor().completas()}">${v.valor().completas()} de ${v.valor().total()}</meter>
  </p>
  <ol class="lista-verificacion">
    @for(ResultadoToulmin.ParteEvaluada p : v.valor().partes())
      <li class="parte parte-${p.estado().toString()}" data-parte="${p.parte().toString()}">
        <span class="${v.claseEstado(p)}">${p.estado().texto()}</span>
        <strong>${p.parte().nombre()}</strong>
        @if(p.texto() != null)
          <span class="texto-parte">${p.texto()}</span>
        @endif
        @if(!v.complemento(p).isEmpty())
          <span class="detalle">· ${v.complemento(p)}</span>
        @endif
        @if(p.falta() != null)
          <p class="falta">${p.falta()}</p>
        @endif
      </li>
    @endfor
  </ol>
  <div class="tarjeta-resultado" role="status">
    <p class="titular">${v.resumen()}</p>
    <p>${v.tarjeta()}</p>
  </div>
  @if(v.lectura() && v.idEjecucion().isPresent())
    <p class="acciones-lectura"><a href="/ejecuciones/${v.idEjecucion().get().toString()}">Abrir en la técnica</a></p>
  @endif
</section>
