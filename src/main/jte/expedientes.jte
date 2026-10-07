@import pensamiento.web.Pagina
@import pensamiento.nucleo.Expediente
@import java.util.List
@param Pagina pagina
@param List<Expediente> expedientes
@param String error
@template.layout(pagina = pagina, contenido = @`
  <section class="expedientes" aria-labelledby="expedientes-titulo">
    <h1 id="expedientes-titulo">Expedientes</h1>
    <p class="detalle">Un expediente es un asunto con nombre que junta ejecuciones de cualquier técnica.</p>
    <form method="post" action="/expedientes" class="nuevo-expediente" hx-post="/expedientes" hx-target="#principal" hx-select="#principal" novalidate>
      <input type="hidden" name="_csrf" value="${pagina.csrf()}">
      <label for="nuevo-expediente-nombre">Nombre del expediente nuevo</label>
      <input type="text" id="nuevo-expediente-nombre" name="nombre" maxlength="120" required
             aria-invalid="${error.isEmpty() ? null : "true"}" aria-describedby="${error.isEmpty() ? null : "nuevo-expediente-error"}">
      @if(!error.isEmpty())
        <p class="error-campo" id="nuevo-expediente-error" role="alert"><span class="chip chip-error">error</span> ${error}</p>
      @endif
      <button type="submit" class="boton">Crear expediente</button>
    </form>
    @if(expedientes.isEmpty())
      <div class="tarjeta vacio"><p>No tienes expedientes. Mira cómo se ve uno en el <a href="/expedientes/muestra">expediente de muestra</a>.</p></div>
    @else
      <ul class="lista-expedientes">
        @for(Expediente x : expedientes)
          <li><a href="/expedientes/${x.id().toString()}">${x.nombre()}</a> <span class="chip">${x.estado().name().toLowerCase()}</span></li>
        @endfor
      </ul>
    @endif
  </section>
`)
