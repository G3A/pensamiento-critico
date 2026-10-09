@import pensamiento.web.biblioteca.VistaBiblioteca
@import pensamiento.nucleo.Documento
@param VistaBiblioteca v
@param String mensaje
@param boolean error
<div id="lista-documentos" class="lista-documentos" hx-get="${v.enProceso() ? "/biblioteca/lista" : null}"
     hx-trigger="${v.enProceso() ? "every 3s" : null}" hx-swap="${v.enProceso() ? "outerHTML" : null}">
  @if(!mensaje.isEmpty())
    <p id="mensaje-subida" class="${error ? "error-campo" : "aviso-ia"}" role="${error ? "alert" : "status"}">
      <span class="${error ? "chip chip-error" : "chip chip-ok"}">${error ? "rechazado" : "listo"}</span> ${mensaje}</p>
  @endif
  <p class="resumen-biblioteca" role="status">${v.documentos().size()} ${v.documentos().size() == 1 ? "documento" : "documentos"} · ${v.fragmentos()}
    ${v.fragmentos() == 1 ? "fragmento" : "fragmentos"} · bge-m3@if(v.enProceso()) · <span class="detalle">la lista se actualiza sola mientras algo se procesa</span>@endif</p>
  @if(v.documentos().isEmpty())
    <div class="tarjeta vacio"><p>Todavía no importaste documentos. Empieza por un informe, una encuesta o un acta que quieras citar.</p></div>
  @else
    <ul class="documentos" aria-label="Documentos de la biblioteca">
      @for(Documento d : v.documentos())
        <li data-documento="${d.id().toString()}" data-estado="${d.estado().enBaseDeDatos()}">
          <span class="${VistaBiblioteca.claseEstado(d)}">${VistaBiblioteca.estado(d)}</span>
          <strong class="nombre-documento">${d.nombre()}</strong>
          <span class="detalle">· ${VistaBiblioteca.detalle(d)} · ${d.compartido() ? "compartido con tu institución" : "privado"}@if(!v.propio(d)) · de otra persona@endif</span>
          @if(d.error().isPresent())
            <p class="detalle error-documento">${d.error().get()}</p>
          @endif
          <p class="acciones-documento">
            @if(d.conOriginal())<a href="/biblioteca/${d.id().toString()}/original" download>Descargar el original</a>@else<span class="detalle">original no incluido en el respaldo</span>@endif
            @if(v.propio(d))
              <button type="button" class="boton boton-secundario boton-chico" hx-post="/biblioteca/${d.id().toString()}/compartir"
                      hx-vals='{"compartido": "${d.compartido() ? "false" : "true"}"}' hx-target="#lista-documentos" hx-swap="outerHTML">
                ${d.compartido() ? "Dejar de compartir" : "Compartir con mi institución"}</button>
              <button type="button" class="boton boton-secundario boton-chico" hx-post="/biblioteca/${d.id().toString()}/borrar"
                      hx-confirm="¿Borrar ${d.nombre()}? Las fuentes que lo citan quedan como documento retirado." hx-target="#lista-documentos"
                      hx-swap="outerHTML">Borrar</button>
            @endif
          </p>
        </li>
      @endfor
    </ul>
  @endif
</div>
