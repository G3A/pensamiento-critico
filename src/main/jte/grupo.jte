@import pensamiento.web.Pagina
@import pensamiento.web.aula.ControladorGrupos.VistaGrupo
@import pensamiento.nucleo.puertos.RepositorioAula.Miembro
@import pensamiento.nucleo.Usuario
@param Pagina pagina
@param VistaGrupo v
<%-- /grupos/{id} (hito 10): el grupo de un docente. Sus estudiantes por nombre y estado de la cuenta; nunca lo que escriben. --%>
@template.layout(pagina = pagina, contenido = @`
  <section class="grupo" aria-labelledby="grupo-titulo">
    <p class="migas"><a href="/grupos">@if(v.administra())Grupos@else Mis grupos@endif</a></p>
    <h1 id="grupo-titulo">${v.grupo().nombre()}</h1>
    <p class="detalle">Docente: ${v.docente()} · ${v.miembros().size()}@if(v.miembros().size() == 1) estudiante@else estudiantes@endif</p>
    @if(v.aviso().isPresent() && !v.aviso().get().isEmpty())
      <p class="aviso" role="status">${v.aviso().get()}</p>
    @endif
    @if(v.error().isPresent())
      <p class="error" role="alert">${v.error().get()}</p>
    @endif
    <section class="tarjeta acciones-grupo" aria-labelledby="clase-titulo">
      <h2 id="clase-titulo">Clase</h2>
      @if(v.claseAbierta())
        <p><span class="chip chip-ok">clase abierta</span></p>
        <p><a class="boton" href="/grupos/${v.grupo().id().toString()}/clase">Ver el código y el QR</a></p>
      @else
        <p><a class="boton" href="/grupos/${v.grupo().id().toString()}/clase">Abrir clase</a></p>
      @endif
      <details class="plegable">
        <summary>Opciones de la clase</summary>
        <form class="formulario" method="post" action="/grupos/${v.grupo().id().toString()}/opciones">
          <input type="hidden" name="_csrf" value="${pagina.csrf()}">
          <label class="opcion"><input type="checkbox" name="lista" value="si" checked="${v.grupo().listaEnClase()}"> Mostrar la lista de nombres del grupo en la entrada de la clase</label>
          <p class="ayuda">Solo mientras la clase está abierta y solo con su código. Igual hace falta la contraseña de cada quien.</p>
          <label class="opcion"><input type="checkbox" name="ia" value="si" checked="${v.grupo().iaEnClase()}"> Dejar usar el asistente de IA durante la clase</label>
          <p class="ayuda">Apagado, las técnicas funcionan igual sin «Pedir propuestas al modelo». Encendido, el grupo tiene un turno justo en la cola del modelo.</p>
          <button type="submit" class="boton boton-secundario">Guardar opciones</button>
        </form>
      </details>
    </section>
    <section class="tarjeta" aria-labelledby="tema-titulo">
      <h2 id="tema-titulo">Reto del grupo</h2>
      <p class="detalle">
        @if(v.grupo().tema().isPresent())
          «Reto de hoy» toma de ${pensamiento.tecnicas.f8.TemaDojo.de(v.grupo().tema().get()).cita()}@if(v.grupo().temaHasta().isPresent()) hasta el ${v.grupo().temaHasta().get().toString()}@endif.
        @else
          Sin tema asignado: «Reto de hoy» invita a practicar en el Dojo.
        @endif
        El Dojo de cada estudiante sigue siendo suyo: sus repasos y su nivel no cambian.
      </p>
      <form class="formulario" method="post" action="/grupos/${v.grupo().id().toString()}/tema">
        <input type="hidden" name="_csrf" value="${pagina.csrf()}">
        <label for="tema-grupo">Tema del Dojo</label>
        <select id="tema-grupo" name="tema">
          <option value="">Ninguno</option>
          @for(pensamiento.tecnicas.f8.TemaDojo t : pensamiento.tecnicas.f8.TemaDojo.values())
            <option value="${t.clave()}" selected="${v.grupo().tema().map(t.tecnica()::equals).orElse(false)}">${t.cita()}</option>
          @endfor
        </select>
        <label for="tema-hasta">Hasta (opcional)</label>
        <input id="tema-hasta" name="hasta" type="date" value="${v.grupo().temaHasta().map(Object::toString).orElse("")}">
        <button type="submit" class="boton boton-secundario">Asignar</button>
      </form>
    </section>
    <section class="tarjeta" aria-labelledby="estudiantes-titulo">
      <h2 id="estudiantes-titulo">Estudiantes</h2>
      <p><a class="boton" href="/grupos/${v.grupo().id().toString()}/lote">Dar de alta en lote</a></p>
      @if(v.miembros().isEmpty())
        <p class="detalle">Este grupo todavía no tiene estudiantes: pega la lista del curso en «Dar de alta en lote».</p>
      @else
        <form method="post" action="/grupos/${v.grupo().id().toString()}/codigos">
          <input type="hidden" name="_csrf" value="${pagina.csrf()}">
          <ul class="miembros">
            @for(Miembro m : v.miembros())
              <li class="miembro">
                <label class="opcion">
                  <input type="checkbox" name="estudiante" value="${m.id().toString()}" disabled="${!m.activo()}">
                  <span class="miembro-nombre">${m.nombre()}</span>
                </label>
                @if(!m.activo())
                  <span class="chip chip-aviso">desactivada</span>
                @elseif(!m.tieneClave())
                  <span class="chip">sin canjear su código</span>
                @else
                  <span class="chip chip-ok">activa</span>
                @endif
              </li>
            @endfor
          </ul>
          <p class="ayuda">Si alguien perdió su código o olvidó su contraseña, márcalo y genera uno nuevo: sus sesiones abiertas se cierran.</p>
          <button type="submit" class="boton boton-secundario">Códigos nuevos para los marcados</button>
        </form>
      @endif
    </section>
    @if(v.administra())
      <details class="tarjeta plegable">
        <summary>Cambiar el docente</summary>
        <form class="formulario" method="post" action="/grupos/${v.grupo().id().toString()}/docente">
          <input type="hidden" name="_csrf" value="${pagina.csrf()}">
          <label for="docente-grupo">Docente</label>
          <select id="docente-grupo" name="docente">
            <option value="">Sin docente</option>
            @for(Usuario d : v.docentes())
              <option value="${d.id().toString()}" selected="${v.grupo().docenteId().map(d.id()::equals).orElse(false)}">${d.nombre()}</option>
            @endfor
          </select>
          <button type="submit" class="boton boton-secundario">Guardar</button>
        </form>
      </details>
    @endif
  </section>
`)
