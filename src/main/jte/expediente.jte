@import pensamiento.web.Pagina
@import pensamiento.web.expedientes.VistaExpediente
@import pensamiento.expediente.ServicioExpedientes
@param Pagina pagina
@param VistaExpediente v
@template.layout(pagina = pagina, contenido = @`
  <section class="expediente" id="${v.idRaiz()}" aria-labelledby="expediente-titulo">
    @if(v.muestra())
      <p class="aviso-muestra" role="note"><span class="chip chip-pendiente">muestra · solo lectura</span>
        Así se ve un asunto con técnicas aplicadas. Es un ejemplo del catálogo: no es tuyo ni se puede cambiar.</p>
    @endif
    <h1 id="expediente-titulo">${v.expediente().nombre()}</h1>
    <p class="chips">
      <span class="chip">${v.expediente().estado().name().toLowerCase()}</span>
      <span class="chip">${v.lineaDeTiempo().size()} ${v.lineaDeTiempo().size() == 1 ? "ejecución" : "ejecuciones"}</span>
      <span class="chip">${v.familiasConEjecuciones()} de 8 familias</span>
      <span class="chip">${v.faltaParaCerrar().size()} ${v.faltaParaCerrar().size() == 1 ? "pendiente" : "pendientes"}</span>
    </p>

    <div class="expediente-cuerpo">
      <section class="linea-de-tiempo" aria-labelledby="linea-titulo">
        <h2 id="linea-titulo">Línea de tiempo</h2>
        @if(v.lineaDeTiempo().isEmpty())
          <div class="tarjeta vacio"><p>Este expediente todavía no tiene ejecuciones. En la pestaña Usar de una técnica, guarda una ejecución y asóciala aquí.</p>
            <p><a class="boton" href="/catalogo">Ir al catálogo</a></p></div>
        @else
          <ol class="linea">
            @for(VistaExpediente.Item item : v.lineaDeTiempo())
              <li class="item-linea" id="linea-${item.ejecucion().id().toString()}">
                <p><span class="chip">${item.tecnica().familia()}</span> <strong>${item.tecnica().cita()}</strong> · ${item.fecha()}</p>
                <p>${item.ejecucion().resumen()}</p>
                <details>
                  <summary>Ver el resultado</summary>
                  ${item.resultado()}
                </details>
              </li>
            @endfor
          </ol>
        @endif
      </section>

      <aside class="resumen-familias" aria-labelledby="resumen-titulo">
        <h2 id="resumen-titulo">Resumen por familia</h2>
        <ul>
          @for(ServicioExpedientes.PorFamilia pf : v.porFamilia())
            <li class="${pf.vacia() ? "familia-vacia" : "familia-con-ejecuciones"}">
              <strong>${pf.familia().codigo()} ${pf.familia().nombre()}</strong> ·
              @if(pf.vacia())
                sin ejecuciones
                @if(pf.sugerida().isPresent())
                  · sugerida: <a href="/tecnicas/${pf.sugerida().get().id().valor()}">${pf.sugerida().get().cita()}</a>
                @endif
              @else
                ${pf.ejecuciones().size()} ${pf.ejecuciones().size() == 1 ? "ejecución" : "ejecuciones"} · ${pf.pendientes()} ${pf.pendientes() == 1 ? "pendiente" : "pendientes"}
              @endif
            </li>
          @endfor
        </ul>

        <h2>Qué falta para cerrar</h2>
        @if(v.faltaParaCerrar().isEmpty())
          <p class="detalle">Nada pendiente en las ejecuciones de este expediente.</p>
        @else
          <ol class="falta-para-cerrar">
            @for(VistaExpediente.Falta f : v.faltaParaCerrar())
              <li><span class="chip chip-aviso">${f.pendiente().pendiente().tipo().name().toLowerCase()}</span>
                @if(f.enlace().isEmpty())
                  ${f.pendiente().pendiente().descripcion()}
                @else
                  <a href="${f.enlace()}">${f.pendiente().pendiente().descripcion()}</a>
                @endif
              </li>
            @endfor
          </ol>
        @endif
      </aside>
    </div>

    @if(!v.muestra())
      <div class="acciones">
        <a class="boton boton-secundario" href="/catalogo">Abrir una técnica</a>
        <form method="post" action="/expedientes/${v.expediente().id().toString()}/borrar" class="en-linea"
              hx-post="/expedientes/${v.expediente().id().toString()}/borrar" hx-confirm="¿Borrar este expediente? Sus ejecuciones siguen en el historial de cada técnica.">
          <input type="hidden" name="_csrf" value="${pagina.csrf()}">
          <button type="submit" class="boton boton-peligro">Borrar expediente</button>
        </form>
      </div>
    @else
      <p><a class="boton" href="/catalogo">Ver el catálogo</a></p>
    @endif
  </section>
`)
