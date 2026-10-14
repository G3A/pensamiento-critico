@import pensamiento.web.Pagina
@import pensamiento.web.aula.ControladorGrupos.VistaGrupos
@import pensamiento.web.aula.ControladorGrupos.FilaGrupo
@import pensamiento.nucleo.Usuario
@param Pagina pagina
@param VistaGrupos v
<%-- /grupos (hito 10, RF-19): quien administra ve todos y los crea; cada docente ve solo los suyos. --%>
@template.layout(pagina = pagina, contenido = @`
  <section class="grupos" aria-labelledby="grupos-titulo">
    <h1 id="grupos-titulo">@if(v.administra())Grupos@else Mis grupos@endif</h1>
    @if(v.administra())
      <p class="detalle">Un grupo es un curso con su docente. El docente da de alta a sus estudiantes, abre la clase y ve el panel
        del grupo, que solo trae números: nunca lo que escribe cada estudiante.</p>
    @else
      <p class="detalle">Tus grupos: entra a uno para dar de alta a sus estudiantes, abrir la clase o ver su panel.</p>
    @endif
    @if(v.error().isPresent())
      <p class="error" role="alert">${v.error().get()}</p>
    @endif
    @if(v.filas().isEmpty())
      <div class="tarjeta vacio">
        <p>@if(v.administra())Todavía no hay grupos.@else Todavía no tienes grupos: pídele a quien administra la app que te asigne uno.@endif</p>
      </div>
    @else
      <ul class="lista-grupos">
        @for(FilaGrupo f : v.filas())
          <li class="tarjeta grupo-fila">
            <a class="grupo-nombre" href="/grupos/${f.grupo().id().toString()}">${f.grupo().nombre()}</a>
            <span class="detalle">${f.estudiantes()} @if(f.estudiantes() == 1)estudiante@else estudiantes@endif@if(v.administra()) · ${f.docente()}@endif</span>
          </li>
        @endfor
      </ul>
    @endif
    @if(v.administra())
      <details class="tarjeta plegable" open="${v.error().isPresent() || v.filas().isEmpty()}">
        <summary>Crear un grupo</summary>
        <form class="formulario" method="post" action="/grupos">
          <input type="hidden" name="_csrf" value="${pagina.csrf()}">
          <label for="nombre-grupo">Nombre del grupo</label>
          <input id="nombre-grupo" name="nombre" type="text" maxlength="60" required autocomplete="off" value="${v.nombreEscrito()}">
          <p class="ayuda">Por ejemplo «10.º A · Filosofía 2026». Un docente puede tener varios grupos.</p>
          <label for="docente-grupo">Docente</label>
          <select id="docente-grupo" name="docente">
            <option value="">Sin docente por ahora</option>
            @for(Usuario d : v.docentes())
              <option value="${d.id().toString()}">${d.nombre()}</option>
            @endfor
          </select>
          @if(v.docentes().isEmpty())
            <p class="ayuda">Para asignar un docente, primero crea su cuenta en <a href="/usuarios">Usuarios</a> con el rol «Docente».</p>
          @endif
          <button type="submit" class="boton">Crear grupo</button>
        </form>
      </details>
    @endif
  </section>
`)
