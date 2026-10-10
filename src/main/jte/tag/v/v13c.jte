@import pensamiento.web.patrones.V13c
@param V13c v
<section id="${v.idRaiz()}" data-patron="V13c" data-modo="${v.modo().name().toLowerCase()}" class="resultado v13c" aria-labelledby="${v.idRaiz()}-titulo">
  <h3 id="${v.idRaiz()}-titulo" class="titulo-resultado">${v.titulo()}</h3>
  <ol class="calendario" aria-label="Repasos de los próximos 7 días">
    @for(V13c.Dia d : v.dias())
      <li class="dia${d.hoy() ? " dia-hoy" : ""}${d.repasos() == 0 ? " dia-libre" : ""}" data-dia="${d.fecha()}">
        <time datetime="${d.fecha()}">${d.nombre()}</time>
        <span class="repasos-dia">${d.repasos()} ${d.repasos() == 1 ? "repaso" : "repasos"}</span>
        @if(d.hoy())<span class="chip chip-pendiente">hoy</span>@endif
      </li>
    @endfor
  </ol>
  @if(!v.conceptos().isEmpty())
    <h4 class="subtitulo-resultado">Conceptos</h4>
    <ul class="conceptos-repaso">
      @for(V13c.Concepto c : v.conceptos())
        <li class="${c.toca() ? "concepto-toca" : "concepto"}">
          <span class="${c.toca() ? "chip chip-pendiente" : "chip"}">${c.cuando()}</span>
          <strong>${c.nombre()}</strong> <span class="detalle">(${c.tema()}) · ${c.detalle()}</span>
        </li>
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
