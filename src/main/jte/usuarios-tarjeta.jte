@import pensamiento.web.Pagina
@import pensamiento.web.usuarios.ServicioUsuarios.CodigoEmitido
@import pensamiento.nucleo.puertos.RepositorioUsuarios.TipoCodigo
@param Pagina pagina
@param CodigoEmitido emitido
@param String ruta
@param java.time.ZoneId zona
<%-- Tarjeta imprimible con el código de un uso (hito 9): se muestra una sola vez; nunca lleva una contraseña. --%>
@template.layout(pagina = pagina, contenido = @`
  <section class="tarjeta-codigo-pagina">
    <p class="no-imprimir aviso" role="status">
      @if(emitido.tipo() == TipoCodigo.ALTA)
        Cuenta creada. Imprime o copia este código ahora: no se vuelve a mostrar.
      @else
        Las sesiones abiertas de esta persona se cerraron. Imprime o copia este código ahora: no se vuelve a mostrar.
      @endif
    </p>
    <article class="tarjeta tarjeta-codigo" aria-labelledby="tarjeta-titulo">
      <h1 id="tarjeta-titulo">${emitido.cuenta().nombre()}</h1>
      <p>@if(emitido.tipo() == TipoCodigo.ALTA)Tu código de alta@else Tu código para elegir una contraseña nueva@endif</p>
      <p class="codigo-grande" translate="no">${emitido.legible()}</p>
      <ol>
        <li>Entra a <strong>…/${ruta}</strong> en el navegador.</li>
        <li>Toca <strong>«Tengo un código»</strong> y escribe este código.</li>
        <li>Elige tu contraseña: @if(emitido.cuenta().rol().exigeLlave())12@else 10@endif caracteres o más; una frase sirve. No se la digas a nadie.</li>
      </ol>
      <p class="detalle">Sirve una sola vez y vence el ${pensamiento.web.tecnicas.Fechas.corta(emitido.caducaEn(), zona)}.</p>
    </article>
    <p class="no-imprimir"><a class="boton" href="/usuarios">Volver a Usuarios</a></p>
  </section>
`)
