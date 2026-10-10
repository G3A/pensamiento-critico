@import pensamiento.web.Pagina
@import pensamiento.web.dojo.ControladorDojo
@import pensamiento.flujos.DojoDeRazonamiento
@param Pagina pagina
@param ControladorDojo.VistaDojo v
!{DojoDeRazonamiento.Respuesta r = v.respuesta().get();}
<section id="reto" class="reto tarjeta respuesta-reto" aria-labelledby="respuesta-titulo" data-reto="${r.reto().id()}"
         data-resultado="${r.acierto() ? "acierto" : "error"}">
  @if(r.reto().esDeEscribir())
    <h2 id="respuesta-titulo"><span class="${r.acierto() ? "chip chip-ok" : "chip chip-aviso"}">${r.acierto() ? "acierto" : "error"}</span>
      ${r.acierto() ? "Tu versión cumple la rúbrica." : "Tu versión todavía no cumple la rúbrica."}</h2>
    <p class="detalle">Escribiste: «${r.respuesta()}»</p>
    <ul class="chequeos-rubrica">
      @for(DojoDeRazonamiento.ChequeoRevisado c : r.chequeos())
        <li><span class="${c.cumple() ? "chip chip-ok" : "chip chip-aviso"}">${c.cumple() ? "cumple" : "no cumple"}</span> ${c.texto()}</li>
      @endfor
    </ul>
    <p class="detalle">La rúbrica mira palabras, no el sentido. Una versión posible: «${r.reto().respuestaModelo()}»</p>
  @else
    <h2 id="respuesta-titulo"><span class="${r.acierto() ? "chip chip-ok" : "chip chip-aviso"}">${r.acierto() ? "acierto" : "error"}</span>
      ${r.acierto() ? "Acierto." : "No es esa: la respuesta es «" + r.correcta().map(o -> o.texto()).orElse("") + "»."}</h2>
    @if(!r.acierto() && r.elegida().isPresent())
      <p class="detalle">Elegiste: «${r.elegida().get().texto()}»</p>
    @endif
  @endif
  <p class="explicacion"><strong>Por qué:</strong> ${r.reto().explicacion()}</p>
  <p class="repaso-reto">${r.siguienteRepaso()}</p>
  <p class="progreso-reto">${r.progreso()}</p>
  @if(r.dominaste().isPresent())
    <p class="aviso-lista aviso-nota" role="status"><span class="chip chip-ok">nivel</span> ${r.dominaste().get()}</p>
  @endif
  <p class="detalle">Hoy: ${r.hechosHoy()} de ${r.limite()} retos · racha ${r.racha()}</p>
  <p class="acciones-reto">
    <a href="${v.urlSiguiente()}" class="boton" data-accion="siguiente" autofocus>Siguiente reto</a>
    <a href="/dojo/progreso" class="boton boton-secundario">Ver progreso</a>
  </p>
</section>
