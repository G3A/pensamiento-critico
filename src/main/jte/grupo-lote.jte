@import pensamiento.web.Pagina
@import pensamiento.web.aula.ControladorAltaEnLote.VistaLote
@import pensamiento.web.aula.ListaDeEstudiantes
@param Pagina pagina
@param VistaLote v
<%-- Alta en lote (hito 10, entregable 3): pegar una lista o subir un CSV; vista previa antes de crear nada. --%>
@template.layout(pagina = pagina, contenido = @`
  <section class="grupo-lote" aria-labelledby="lote-titulo">
    <p class="migas"><a href="/grupos/${v.grupo().id().toString()}">${v.grupo().nombre()}</a></p>
    <h1 id="lote-titulo">Dar de alta en lote</h1>
    @if(v.error().isPresent())
      <p class="error" role="alert">${v.error().get()}</p>
    @endif
    @if(v.previa().isEmpty())
      <p class="detalle">Una persona por línea, hasta ${ListaDeEstudiantes.MAXIMO}. Antes de crear nada vas a ver qué filas se crean y
        cuáles no. Cada estudiante recibe un código de alta en una tarjeta para imprimir; con él elige su contraseña.</p>
      <form class="formulario tarjeta" method="post" action="/grupos/${v.grupo().id().toString()}/lote/vista-previa" enctype="multipart/form-data">
        <input type="hidden" name="_csrf" value="${pagina.csrf()}">
        <label for="lista">Pega la lista</label>
        <textarea id="lista" name="texto" rows="12" autocomplete="off" spellcheck="false">${v.texto()}</textarea>
        <p class="ayuda">Por ejemplo, copiada de la planilla del curso. Si un nombre lleva coma, ponlo entre comillas.</p>
        <label for="archivo">O sube un CSV</label>
        <input id="archivo" name="archivo" type="file" accept=".csv,text/csv,text/plain">
        <p class="ayuda">Se toma la primera columna; una primera fila «nombre» se salta.</p>
        <button type="submit" class="boton">Ver qué se va a crear</button>
      </form>
    @else
      @if(v.previa().get().nuevas() == 0)
        <p class="aviso" role="status">Ninguna fila se puede crear: revisa la lista.</p>
      @else
        <p class="aviso" role="status">Se van a crear ${v.previa().get().nuevas()}@if(v.previa().get().nuevas() == 1) cuenta@else cuentas@endif de
          estudiante en ${v.grupo().nombre()}. Las filas marcadas no se crean.</p>
      @endif
      <table class="vista-previa">
        <caption class="detalle">Vista previa: ${v.previa().get().filas().size()} filas</caption>
        <thead><tr><th scope="col">Línea</th><th scope="col">Nombre</th><th scope="col">Qué pasa</th></tr></thead>
        <tbody>
          @for(ListaDeEstudiantes.Fila f : v.previa().get().filas())
            <tr class="fila-${f.estado().name().toLowerCase()}">
              <td>${f.linea()}</td>
              <td>${f.nombre()}</td>
              <td>@if(f.estado() == ListaDeEstudiantes.Estado.NUEVA)<span class="chip chip-ok">${f.estado().texto()}</span>@else<span class="chip chip-aviso">${f.estado().texto()}</span>@endif</td>
            </tr>
          @endfor
        </tbody>
      </table>
      <div class="acciones-lote">
        @if(v.previa().get().nuevas() > 0)
          <form method="post" action="/grupos/${v.grupo().id().toString()}/lote">
            <input type="hidden" name="_csrf" value="${pagina.csrf()}">
            <input type="hidden" name="texto" value="${v.texto()}">
            <button type="submit" class="boton">Crear ${v.previa().get().nuevas()} y ver las tarjetas</button>
          </form>
        @endif
        <a class="boton boton-secundario" href="/grupos/${v.grupo().id().toString()}/lote">Corregir la lista</a>
      </div>
    @endif
  </section>
`)
