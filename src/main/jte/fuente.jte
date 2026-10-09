@import pensamiento.web.Pagina
@import pensamiento.web.verificacion.VistaFuente
@param Pagina pagina
@param VistaFuente v
@template.layout(pagina = pagina, contenido = @`
  <section class="ficha-fuente" aria-labelledby="fuente-titulo" data-afirmacion="${v.id()}">
    <p class="migas"><a href="/verificar">Verificación</a> · <a href="/verificar/${v.id()}">Ficha</a> · Fuente nueva</p>
    <h1 id="fuente-titulo">Ficha de fuente</h1>
    <p class="detalle">Para la afirmación «${v.afirmacion().texto()}». Un solo formulario cubre T19 · SIFT, T20 · Lectura lateral, T21 · CRAAP,
      T22 · Triangulación y T23 · Jerarquía de evidencia.</p>
    @if(v.hayErrores())
      <p class="error-campo" role="alert"><span class="chip chip-error">error</span> Revisa los campos marcados.</p>
    @endif
    <div class="ficha-cuerpo">
      <form id="form-fuente" class="formulario-fuente tarjeta" method="post" action="/verificar/${v.id()}/fuentes" hx-post="/verificar/${v.id()}/fuentes"
            hx-target="#principal" hx-select="#principal" hx-swap="outerHTML" novalidate>
        <input type="hidden" name="_csrf" value="${pagina.csrf()}">
        <input type="hidden" name="fragmentoId" value="${v.valor("fragmentoId")}">
        <input type="hidden" name="etiquetadaPor" value="${v.valor("etiquetadaPor")}">
        <input type="hidden" name="propuesta" value="${v.valor("propuesta")}">
        <fieldset class="campo">
          <legend>Datos de la fuente</legend>
          <p class="campo"><label for="f-titulo">Título <span class="obligatorio">(obligatorio)</span></label>
            <input type="text" id="f-titulo" name="titulo" maxlength="160" value="${v.valor("titulo")}"
                   aria-invalid="${v.error("titulo").isEmpty() ? null : "true"}" aria-describedby="${v.error("titulo").isEmpty() ? null : "e-titulo"}">
            @if(!v.error("titulo").isEmpty())<span class="error-campo" id="e-titulo">${v.error("titulo")}</span>@endif</p>
          <p class="campo"><label for="f-autor">Autor u organización</label>
            <input type="text" id="f-autor" name="autor" maxlength="160" value="${v.valor("autor")}"></p>
          <p class="campo"><label for="f-fecha">Fecha</label>
            <input type="date" id="f-fecha" name="fecha" value="${v.valor("fecha")}" aria-invalid="${v.error("fecha").isEmpty() ? null : "true"}">
            @if(!v.error("fecha").isEmpty())<span class="error-campo">${v.error("fecha")}</span>@endif</p>
          <fieldset class="campo"><legend>Tipo <span class="obligatorio">(obligatorio)</span></legend>
            <div class="radios">
              <label class="radio"><input type="radio" name="tipoFuente" value="primaria" checked="${v.valor("tipoFuente").equals("primaria")}"> Primaria</label>
              <label class="radio"><input type="radio" name="tipoFuente" value="secundaria" checked="${v.valor("tipoFuente").equals("secundaria")}"> Secundaria</label>
              <label class="radio"><input type="radio" name="tipoFuente" value="terciaria" checked="${v.valor("tipoFuente").equals("terciaria")}"> Terciaria</label>
            </div>
            @if(!v.error("tipoFuente").isEmpty())<span class="error-campo">${v.error("tipoFuente")}</span>@endif
          </fieldset>
          <p class="campo"><label for="f-diseno">Diseño del estudio</label>
            <select id="f-diseno" name="diseno">
              <option value="no_aplica" selected="${v.valor("diseno").isEmpty() || v.valor("diseno").equals("no_aplica")}">No aplica</option>
              <option value="revision_sistematica" selected="${v.valor("diseno").equals("revision_sistematica")}">Revisión sistemática</option>
              <option value="ensayo_controlado" selected="${v.valor("diseno").equals("ensayo_controlado")}">Ensayo controlado</option>
              <option value="observacional" selected="${v.valor("diseno").equals("observacional")}">Observacional</option>
              <option value="opinion_experto" selected="${v.valor("diseno").equals("opinion_experto")}">Opinión de experto</option>
              <option value="testimonio" selected="${v.valor("diseno").equals("testimonio")}">Testimonio</option>
            </select></p>
          <p class="campo"><label for="f-grupo">Grupo de origen</label>
            <input type="text" id="f-grupo" name="grupo" maxlength="120" value="${v.valor("grupo")}" aria-describedby="ayuda-grupo">
            <span class="ayuda" id="ayuda-grupo">La organización, el autor o la cadena de citas. Dos fuentes del mismo grupo no son independientes entre sí (R03).</span></p>
          @if(v.desdeBiblioteca())
            <p class="campo detalle">Ubicación: biblioteca, ${v.documento()}@if(!v.pagina().isEmpty()), p. ${v.pagina()}@endif</p>
          @endif
        </fieldset>
        <fieldset class="campo">
          <legend>Qué hace con la afirmación <span class="obligatorio">(obligatorio)</span></legend>
          @if(v.propuestaDelModelo())
            <p class="propuesta"><span class="chip chip-pendiente">propuesta del modelo</span> ${v.valor("propuesta")}@if(!v.valor("porque").isEmpty()): ${v.valor("porque")}@endif.
              Si la dejas elegida, la evidencia queda «etiquetada por el modelo, adoptada por ti»; si eliges otra, es tuya.</p>
          @endif
          <div class="radios">
            <label class="radio"><input type="radio" name="postura" value="apoya" checked="${v.valor("postura").equals("apoya")}"> Apoya</label>
            <label class="radio"><input type="radio" name="postura" value="contradice" checked="${v.valor("postura").equals("contradice")}"> Contradice</label>
            <label class="radio"><input type="radio" name="postura" value="matiza" checked="${v.valor("postura").equals("matiza")}"> Matiza</label>
          </div>
          @if(!v.error("postura").isEmpty())<span class="error-campo">${v.error("postura")}</span>@endif
        </fieldset>
        <p class="campo"><label for="f-pasaje">Pasaje, copiado tal cual <span class="obligatorio">(obligatorio)</span></label>
          <textarea id="f-pasaje" name="pasaje" rows="4" maxlength="2000" aria-invalid="${v.error("pasaje").isEmpty() ? null : "true"}">${v.valor("pasaje")}</textarea>
          @if(!v.error("pasaje").isEmpty())<span class="error-campo">${v.error("pasaje")}</span>@endif</p>
        <fieldset class="campo">
          <legend>SIFT</legend>
          <p class="campo"><label for="f-sift-fuente">Investigué la fuente</label>
            <textarea id="f-sift-fuente" name="siftFuente" rows="2" maxlength="300">${v.valor("siftFuente")}</textarea></p>
          <p class="campo"><label for="f-sift-cobertura">Mejor cobertura</label>
            <textarea id="f-sift-cobertura" name="siftCobertura" rows="2" maxlength="300">${v.valor("siftCobertura")}</textarea></p>
          <p class="campo"><label for="f-sift-contexto">Contexto original</label>
            <textarea id="f-sift-contexto" name="siftContexto" rows="2" maxlength="300">${v.valor("siftContexto")}</textarea></p>
        </fieldset>
        <fieldset class="campo">
          <legend>Independencia y acceso (lectura lateral)</legend>
          <label class="casilla"><input type="checkbox" name="independiente" value="true" checked="${v.marcado("independiente")}"> Independiente de quien afirma</label>
          <label class="casilla"><input type="checkbox" name="original" value="true" checked="${v.marcado("original")}"> Tuve acceso al original</label>
        </fieldset>
        <fieldset class="campo">
          <legend>CRAAP (0 a 5 cada criterio; los cinco o ninguno)</legend>
          <p class="detalle">Pesos de tu configuración de T21 · CRAAP: ${v.pesos()}.</p>
          @for(String c : v.criterios())
            <p class="campo"><label for="f-${c}">${VistaFuente.nombreCriterio(c)}</label>
              <input type="number" id="f-${c}" name="${c}" min="0" max="5" step="1" value="${v.valor(c)}"></p>
          @endfor
          @if(!v.error("actualidad").isEmpty())<span class="error-campo">${v.error("actualidad")}</span>@endif
        </fieldset>
        <p class="acciones">
          <button type="submit" class="boton" name="despues" value="ficha">Guardar fuente</button>
          <button type="submit" class="boton boton-secundario" name="despues" value="otra">Guardar y añadir otra</button>
          <a href="/verificar/${v.id()}">Volver a la ficha sin guardar</a>
        </p>
      </form>
      <aside class="panel-previa tarjeta" aria-labelledby="previa-titulo">
        <h2 id="previa-titulo">Qué aporta</h2>
        <div id="previa" hx-post="/verificar/${v.id()}/fuentes/previa" hx-include="#form-fuente" hx-trigger="load, change from:#form-fuente"
             hx-swap="innerHTML" aria-live="polite"><p class="detalle">Calculando…</p></div>
      </aside>
    </div>
  </section>
`)
