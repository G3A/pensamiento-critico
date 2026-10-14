@import pensamiento.web.Pagina
@import pensamiento.web.aula.ControladorAltaEnLote.HojaDeTarjetas
@import pensamiento.web.aula.ListaDeEstudiantes
@import pensamiento.web.usuarios.ServicioUsuarios.CodigoEmitido
@import pensamiento.nucleo.puertos.RepositorioUsuarios.TipoCodigo
@param Pagina pagina
@param HojaDeTarjetas h
<%-- Hoja de tarjetas (hito 10, entregable 3): 8 por hoja en dos columnas, que caben igual en A4 y en carta. Los códigos se
     muestran una sola vez; nunca una contraseña. --%>
@template.layout(pagina = pagina, contenido = @`
  <section class="hoja-de-tarjetas-pagina">
    <div class="no-imprimir">
      <p class="migas"><a href="/grupos/${h.grupo().id().toString()}">${h.grupo().nombre()}</a></p>
      <h1>Tarjetas de ${h.grupo().nombre()}</h1>
      <p class="aviso" role="status">
        @if(h.alta())
          ${h.codigos().size()}@if(h.codigos().size() == 1) cuenta creada@else cuentas creadas@endif.
        @else
          ${h.codigos().size()}@if(h.codigos().size() == 1) código nuevo@else códigos nuevos@endif; las sesiones abiertas de esas personas se cerraron.
        @endif
        Imprime esta hoja ahora (8 tarjetas por hoja, A4 o carta): los códigos no se vuelven a mostrar.
      </p>
      @if(!h.sinCrear().isEmpty())
        <details class="tarjeta plegable">
          <summary>${h.sinCrear().size()}@if(h.sinCrear().size() == 1) fila no se creó@else filas no se crearon@endif</summary>
          <ul>
            @for(ListaDeEstudiantes.Fila f : h.sinCrear())
              <li>Línea ${f.linea()}: ${f.nombre()} (${f.estado().texto()})</li>
            @endfor
          </ul>
        </details>
      @endif
    </div>
    <div class="hoja-tarjetas">
      @for(CodigoEmitido c : h.codigos())
        <article class="tarjeta tarjeta-lote">
          <h2 class="tarjeta-lote-nombre">${c.cuenta().nombre()}</h2>
          <p class="tarjeta-lote-para">@if(c.tipo() == TipoCodigo.ALTA)Tu código de alta@else Tu código para elegir una contraseña nueva@endif · ${h.grupo().nombre()}</p>
          <p class="codigo-grande" translate="no">${c.legible()}</p>
          <ol class="tarjeta-lote-pasos">
            <li>Entra a <strong>…/${h.ruta()}</strong> (o escanea el QR de la clase).</li>
            <li>Toca <strong>«Tengo un código»</strong> y escríbelo.</li>
            <li>Elige tu contraseña: 10 caracteres o más; una frase sirve. No se la digas a nadie.</li>
          </ol>
          <p class="detalle">Sirve una vez; vence el ${pensamiento.web.tecnicas.Fechas.corta(c.caducaEn(), h.zona())}.</p>
        </article>
      @endfor
    </div>
    <p class="no-imprimir"><a class="boton" href="/grupos/${h.grupo().id().toString()}">Volver al grupo</a></p>
  </section>
`)
