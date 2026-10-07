@import pensamiento.web.tecnicas.ControladorTecnicas.VistaFicha
@import pensamiento.web.tecnicas.ControladorTecnicas.FilaHistorial
@import pensamiento.web.tecnicas.ComparadorEjecuciones
@param VistaFicha ficha
!{String id = ficha.tecnica().id().valor();}
<div class="historial">
  <h2 class="subtitulo">${ficha.historial().size()} ejecuciones</h2>
  @if(ficha.historial().isEmpty())
    <div class="tarjeta vacio"><p>Todavía no guardaste ninguna ejecución de esta técnica. En la pestaña Usar, "Guardar en historial" la deja aquí.</p></div>
  @else
    <form class="seleccion-historial" action="/tecnicas/${id}" method="get" hx-get="/tecnicas/${id}" hx-target="#ficha-cuerpo" hx-swap="outerHTML" hx-push-url="true">
      <input type="hidden" name="pestana" value="historial">
      <table class="tabla-historial">
        <caption>Elige una ejecución en la columna A y otra en la columna B para compararlas.</caption>
        <thead><tr><th scope="col">A</th><th scope="col">B</th><th scope="col">Ejecución</th><th scope="col">Acciones</th></tr></thead>
        <tbody>
          @for(FilaHistorial f : ficha.historial())
            !{String e = f.ejecucion().id().toString();}
            <tr id="historial-${e}">
              <td><input type="radio" name="a" value="${e}" aria-label="Comparar como A la del ${f.fecha()}"></td>
              <td><input type="radio" name="b" value="${e}" aria-label="Comparar como B la del ${f.fecha()}"></td>
              <td><strong>${f.ejecucion().resumen()}</strong><br><span class="detalle">${f.fecha()} · ${f.config()}</span></td>
              <td><a href="/ejecuciones/${e}">Abrir</a> · <a href="/tecnicas/${id}?reejecutar=${e}">Reejecutar con la configuración actual</a></td>
            </tr>
          @endfor
        </tbody>
      </table>
      <button type="submit" class="boton boton-secundario">Comparar las dos seleccionadas</button>
    </form>
  @endif
  @if(ficha.comparacion() != null)
    !{ComparadorEjecuciones.Comparacion c = ficha.comparacion();}
    <section class="comparacion tarjeta" aria-labelledby="comparacion-titulo">
      <h3 id="comparacion-titulo">Comparación</h3>
      <p><strong>Antes:</strong> ${c.resumenAnterior()}<br><strong>Después:</strong> ${c.resumenPosterior()}</p>
      @if(c.cambiosDeConfiguracion().isEmpty())
        <p class="detalle">Misma configuración.</p>
      @else
        <p>Configuración:</p>
        <ul>@for(String cambio : c.cambiosDeConfiguracion())<li>${cambio}</li>@endfor</ul>
      @endif
      @for(ComparadorEjecuciones.Seccion s : c.secciones())
        <h4>${s.titulo()}</h4>
        <ul class="diferencias">
          @for(ComparadorEjecuciones.Diferencia d : s.diferencias())
            <li class="${d.estado().clase()}"><span class="chip">${d.estado().texto()}</span> ${d.texto()}
              @if(!d.detalle().isEmpty())<span class="detalle"> · ${String.join("; ", d.detalle())}</span>@endif
            </li>
          @endfor
        </ul>
      @endfor
    </section>
  @endif
</div>
