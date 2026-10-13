@import pensamiento.web.Pagina
@import pensamiento.web.operador.ControladorOperador.VistaOperador
@import pensamiento.nucleo.puertos.RepositorioOperador
@param Pagina pagina
@param VistaOperador v
<%-- Panel del operador del servicio (hito 8): instituciones, invitaciones y su auditoría. Nada de las personas. --%>
@template.layout(pagina = pagina, contenido = @`
  <section class="usuarios">
    <h1>Operador del servicio</h1>
    <form method="post" action="/operador/salir">
      <input type="hidden" name="_csrf" value="${pagina.csrf()}">
      <button type="submit" class="boton boton-secundario">Salir</button>
    </form>
    @if(v.invitacion().isPresent())
      <div class="tarjeta" role="status" id="invitacion">
        <h2>Invitación para el primer administrador</h2>
        <p>Código: <strong class="codigo-invitacion">${v.invitacion().get().legible()}</strong></p>
        <p class="detalle">Se muestra solo esta vez. Sirve una sola vez, hasta el ${v.fecha(v.invitacion().get().caducaEn())},
          y deja sin efecto las invitaciones anteriores de esta institución.</p>
        @if(v.invitada().isPresent())
          <p>Quien la recibe entra a <strong>/${v.invitada().get()}/invitacion</strong>, escribe el código, su nombre y su PIN.</p>
        @endif
      </div>
    @endif
    @if(v.error().isPresent())
      <p class="error" role="alert">${v.error().get()}</p>
    @endif
    <form class="tarjeta formulario" method="post" action="/operador/instituciones">
      <input type="hidden" name="_csrf" value="${pagina.csrf()}">
      <h2>Nueva institución</h2>
      <label for="nombre">Nombre</label>
      <input id="nombre" name="nombre" type="text" maxlength="120" required value="${v.nombreEscrito()}">
      <label for="ruta">Ruta de entrada (de 3 a 40 minúsculas sin tildes, números o guiones)</label>
      <input id="ruta" name="ruta" type="text" pattern="[a-z0-9][a-z0-9-]{1,38}[a-z0-9]" required value="${v.rutaEscrita()}"
             autocapitalize="none" spellcheck="false">
      <button type="submit" class="boton">Crear</button>
    </form>
    <table class="tabla">
      <caption>Instituciones del servicio</caption>
      <thead><tr><th scope="col">Nombre</th><th scope="col">Ruta</th><th scope="col">Estado</th><th scope="col">Cuentas</th><th scope="col">Acción</th></tr></thead>
      <tbody>
        @for(RepositorioOperador.InstitucionDelOperador i : v.instituciones())
          <tr>
            <td>${i.institucion().nombre()}</td>
            <td>/${i.institucion().ruta()}</td>
            <td>@if(i.institucion().activa())<span class="chip chip-ok">activa</span>@else<span class="chip chip-aviso">suspendida</span>@endif</td>
            <td>${i.tieneCuentas() ? "con cuentas" : "sin cuentas"}</td>
            <td>
              @if(i.institucion().activa())
                <form method="post" action="/operador/instituciones/${i.institucion().id().toString()}/suspender">
                  <input type="hidden" name="_csrf" value="${pagina.csrf()}">
                  <button type="submit" class="boton boton-secundario">Suspender</button>
                </form>
                @if(!i.tieneCuentas())
                  <form method="post" action="/operador/instituciones/${i.institucion().id().toString()}/invitar">
                    <input type="hidden" name="_csrf" value="${pagina.csrf()}">
                    <button type="submit" class="boton">Invitar al primer administrador</button>
                  </form>
                @endif
              @else
                <form method="post" action="/operador/instituciones/${i.institucion().id().toString()}/reactivar">
                  <input type="hidden" name="_csrf" value="${pagina.csrf()}">
                  <button type="submit" class="boton boton-secundario">Reactivar</button>
                </form>
              @endif
            </td>
          </tr>
        @endfor
      </tbody>
    </table>
    <h2>Auditoría del operador</h2>
    <ul class="lista">
      @for(RepositorioOperador.Evento e : v.auditoria())
        <li>${v.fecha(e.fecha())} · ${e.accion().enBaseDeDatos().replace('_', ' ')} · /${e.ruta() == null ? "" : e.ruta()}</li>
      @endfor
    </ul>
  </section>
`)
