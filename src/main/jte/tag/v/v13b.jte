@import pensamiento.web.patrones.V13b
@param V13b v
<section id="${v.idRaiz()}" data-patron="V13b" data-modo="${v.modo().name().toLowerCase()}" class="resultado v13b" aria-labelledby="${v.idRaiz()}-titulo">
  <h3 id="${v.idRaiz()}-titulo" class="titulo-resultado">${v.titulo()}</h3>
  @if(v.enunciado() != null)
    <p class="enunciado">${v.enunciado()}</p>
  @endif
  <ol class="niveles-bloom">
    @for(V13b.Nivel n : v.niveles())
      <li data-nivel="${n.clave()}" class="nivel-bloom">
        <span class="${n.claseChip()}">${n.estado()}</span>
        <label for="${v.idNivel(n)}"><strong>${n.nombre()}</strong> · ${n.aciertos()} de ${n.meta()} aciertos · ${n.intentos()} ${n.intentos() == 1 ? "intento" : "intentos"}</label>
        <meter id="${v.idNivel(n)}" min="0" max="${n.meta()}" value="${n.barra()}">${n.barra()} de ${n.meta()}</meter>
        <p class="detalle">${n.quePide()}</p>
      </li>
    @endfor
  </ol>
  <p class="mensaje-nivel">${v.mensaje()}</p>
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
