@import pensamiento.web.patrones.V07
@param V07 v
<section id="${v.idRaiz()}" data-patron="V07" data-modo="${v.modo().name().toLowerCase()}" class="resultado v07" aria-labelledby="${v.idRaiz()}-titulo">
  <h3 id="${v.idRaiz()}-titulo" class="titulo-resultado">Diagrama de Ishikawa</h3>
  <p class="enunciado">Efecto: ${v.efecto()}</p>
  <div class="diagrama">
    @if(v.svg().isPresent())
      <figure class="diagrama-svg espina" role="group" aria-label="Espina de pescado dibujada; las categorías de abajo dicen lo mismo con texto">
        $unsafe{v.svg().get()}
      </figure>
    @else
      <p class="detalle" role="status">No se pudo dibujar el diagrama esta vez. Las categorías de abajo tienen las mismas causas.</p>
    @endif
  </div>
  <ul class="categorias-ishikawa" aria-label="Causas por categoría">
    @for(V07.Categoria c : v.categorias())
      <li data-categoria="${c.nombre()}">
        <strong>${c.nombre()}</strong>
        @if(c.estado() != null)<span class="${c.claseChip()}">${c.estado()}</span>@endif
        @if(!c.causas().isEmpty())
          <ul class="causas">
            @for(String causa : c.causas())
              <li>${causa}</li>
            @endfor
          </ul>
        @endif
      </li>
    @endfor
  </ul>
  <div class="tarjeta-resultado" role="status">
    <p class="titular">${v.resumen()}</p>
    <p>${v.tarjeta()}</p>
  </div>
  @if(v.lectura() && v.idEjecucion().isPresent())
    <p class="acciones-lectura"><a href="/ejecuciones/${v.idEjecucion().get().toString()}">Abrir en la técnica</a></p>
  @endif
</section>
