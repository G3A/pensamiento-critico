@import pensamiento.web.taller.VistaTaller
@import pensamiento.nucleo.EstandarPrueba
@import pensamiento.tecnicas.f3.ResultadoFalacias
@param VistaTaller t
<form id="${VistaTaller.ID_FORMULARIO}" class="taller-form" hx-post="/taller/evaluar" hx-target="this" hx-swap="outerHTML" novalidate>
  <input type="hidden" name="textoEvaluado" value="${t.textoEvaluado()}">
  <input type="hidden" id="clave-taller" name="_clave" value="${t.clave()}">
  <div class="taller-editor">
    <div class="campo campo-texto">
      <label for="taller-argdown">Tu argumento, en Argdown</label>
      <textarea id="taller-argdown" name="argdown" rows="10" maxlength="8000" spellcheck="true"
                aria-invalid="${t.tieneError("argdown") ? "true" : null}"
                aria-describedby="taller-argdown-ayuda${t.tieneError("argdown") ? " taller-argdown-error" : ""}">${t.argdown()}</textarea>
      <p id="taller-argdown-ayuda" class="ayuda">Primera línea: la conclusión. Debajo, con dos espacios, <code>+</code> para cada premisa y
        <code>-</code> para cada objeción. <code>#asumible</code> marca un supuesto y <code>#oculta</code> lo que se da por sentado.</p>
      @if(t.tieneError("argdown"))
        <p id="taller-argdown-error" class="error-campo" role="alert"><span class="chip chip-error">error</span> ${t.error("argdown")}</p>
      @endif
    </div>
    <div class="botones-marcado" role="group" aria-label="Agregar una línea marcada">
      <button type="submit" name="_linea" value="conclusion" class="boton boton-secundario" hx-post="/taller/linea">+ conclusión</button>
      <button type="submit" name="_linea" value="premisa" class="boton boton-secundario" hx-post="/taller/linea">+ premisa</button>
      <button type="submit" name="_linea" value="objecion" class="boton boton-secundario" hx-post="/taller/linea">+ objeción</button>
      <button type="submit" name="_linea" value="oculta" class="boton boton-secundario" hx-post="/taller/linea">+ premisa oculta</button>
    </div>
    <div class="campo">
      <label for="taller-estandar">Estándar de prueba del mapa</label>
      <select id="taller-estandar" name="estandar" aria-describedby="taller-estandar-ayuda">
        @for(EstandarPrueba e : t.estandares())
          <option value="${e.toString()}" selected="${e == t.estandar()}">${e.nombre()}</option>
        @endfor
      </select>
      <p id="taller-estandar-ayuda" class="ayuda">Con él se dice, por cada argumento, si es aplicable, y si la conclusión es aceptable (R04).</p>
    </div>
    <fieldset class="extras-toulmin">
      <legend>Lo que el texto no dice, para el panel Toulmin</legend>
      <label for="taller-calificador">Calificador (qué tan seguro estás)</label>
      <input type="text" id="taller-calificador" name="calificador" maxlength="300" value="${t.vacio(t.calificador())}">
      <label for="taller-respaldo">Respaldo de la garantía</label>
      <input type="text" id="taller-respaldo" name="respaldo" maxlength="300" value="${t.vacio(t.respaldo())}">
      <label for="taller-fuente">Fuente del respaldo</label>
      <input type="text" id="taller-fuente" name="fuenteRespaldo" maxlength="300" value="${t.vacio(t.fuenteRespaldo())}">
    </fieldset>
    <p class="acciones">
      <button type="submit" class="boton" hx-disabled-elt="this">Evaluar</button>
      <button type="button" class="boton boton-secundario" hx-post="/taller/guardar" hx-target="#taller-guardado" hx-swap="innerHTML"
              hx-disabled-elt="this">Guardar</button>
    </p>
  </div>
  <div id="taller-resultados" class="taller-resultados" aria-live="polite">
    @if(t.evaluado())
      <section class="panel-taller" aria-labelledby="panel-mapa">
        <h2 id="panel-mapa">Mapa · T01 · Mapeo de argumentos</h2>
        ${t.mapa().get()}
      </section>
      <section class="panel-taller" aria-labelledby="panel-toulmin">
        <h2 id="panel-toulmin">Toulmin · T02 · Modelo de Toulmin</h2>
        @if(t.toulmin().isPresent())
          ${t.toulmin().get()}
        @else
          <p class="detalle" role="status">El panel Toulmin no se pudo armar: ${t.errorToulmin()}</p>
        @endif
      </section>
      <section class="panel-taller" aria-labelledby="panel-falacias">
        <h2 id="panel-falacias">Falacias · T13 · Falacias como esquemas fallidos</h2>
        @if(t.falacias().isPresent())
          ${t.falacias().get()}
        @endif
        @if(!t.marcas().isEmpty())
          <fieldset class="confirmar-marcas">
            <legend>¿Confirmas alguna? La etiqueta de falacia solo cuenta si la confirmas.</legend>
            @for(ResultadoFalacias.Marca m : t.marcas())
              <div class="casilla">
                <input type="checkbox" id="confirmar-${m.codigo()}" name="confirmadas" value="${m.codigo()}" checked="${t.confirmada(m)}">
                <label for="confirmar-${m.codigo()}">Confirmo ${m.codigo()}: ${m.falacia()}</label>
              </div>
            @endfor
            <button type="submit" class="boton boton-secundario">Aplicar confirmaciones</button>
          </fieldset>
        @endif
      </section>
    @else
      <p class="detalle">El mapa, el panel Toulmin y el panel de falacias aparecen aquí al evaluar.</p>
    @endif
  </div>
</form>
