@import pensamiento.web.Pagina
@import pensamiento.web.consejero.VistaConsejero
@import pensamiento.nucleo.Expediente
@param Pagina pagina
@param VistaConsejero.Inicio v
@template.layout(pagina = pagina, contenido = @`
  <section class="consejero" aria-labelledby="consejero-titulo">
    <h1 id="consejero-titulo">Consejero socrático</h1>
    <p class="enunciado">Un asistente que nunca da la respuesta: solo pregunta, y va llenando a la vista los elementos de tu razonamiento.
      El código elige qué preguntar; si el modelo local está, solo redacta esa pregunta con tus palabras.</p>
    @if(!v.modeloDisponible())
      <p class="aviso-ia" role="status"><span class="chip chip-aviso">modo plantillas</span> El modelo no está disponible: las preguntas salen del
        banco de preguntas. Todo funciona igual.</p>
    @endif

    <section class="nueva-sesion" aria-labelledby="nueva-sesion-titulo">
      <h2 id="nueva-sesion-titulo">Nueva sesión</h2>
      @if(!v.error().isEmpty())
        <p class="error" role="alert" id="error-sesion">${v.error()}</p>
      @endif
      <form method="post" action="/consejero/sesiones" id="form-nueva-sesion" novalidate>
        <input type="hidden" name="_csrf" value="${pagina.csrf()}">
        <fieldset class="consejero-modos">
          <legend>Modo</legend>
          @for(String[] m : new String[][] {{"decision", "Decisión: algo que vas a decidir"}, {"ensayo", "Ensayo: algo que vas a escribir o defender"}, {"escalera", "Escalera: de los datos a la acción, un peldaño por turno"}, {"sombreros", "Sombreros: seis miradas por turnos"}, {"debate", "Debate: equipo rojo, steelman, Test de Turing y double crux"}})
            <label><input type="radio" name="modo" value="${m[0]}" checked="${m[0].equals(v.valores().modo())}"> ${m[1]}</label>
          @endfor
        </fieldset>
        <label for="postura">Tu postura</label>
        <textarea id="postura" name="postura" maxlength="300" required aria-describedby="${v.error().isEmpty() ? null : "error-sesion"}">${v.valores().postura()}</textarea>
        <fieldset class="razones-debate">
          <legend>Solo en el modo debate: tus razones y en qué se apoyan</legend>
          @for(int i = 0; i < 3; i++)
            <div class="razon">
              <label for="razon${i}">R${i + 1}</label>
              <input id="razon${i}" name="razon${i}" type="text" maxlength="300" value="${v.valores().razon(i)}">
              <label for="apoyo${i}" class="detalle">¿En qué se apoya?</label>
              <select id="apoyo${i}" name="apoyo${i}">
                @for(String[] a : new String[][] {{"no_se", "No sé"}, {"lo_dijo_alguien", "Lo dijo alguien"}, {"un_caso", "Un caso"}, {"comparacion", "Una comparación"}, {"causa", "Una causa"}, {"consecuencia", "Una consecuencia"}, {"mayoria", "Lo que hace la mayoría"}, {"senal", "Una señal"}, {"definicion", "Una definición"}})
                  <option value="${a[0]}" selected="${a[0].equals(v.valores().apoyo(i))}">${a[1]}</option>
                @endfor
              </select>
            </div>
          @endfor
        </fieldset>
        <label for="confianza">¿Qué tan seguro estás de tu postura? (0 a 100, opcional)</label>
        <input id="confianza" name="confianza" type="number" min="0" max="100" value="${v.valores().confianza()}">
        <label for="expediente">Guardar en</label>
        <select id="expediente" name="expediente">
          <option value="">Un expediente nuevo para esta sesión</option>
          @for(Expediente x : v.expedientes())
            <option value="${x.id().toString()}" selected="${x.id().toString().equals(v.valores().expediente())}">${x.nombre()}</option>
          @endfor
        </select>
        <label class="interruptor"><input type="checkbox" name="usaModelo" value="on" checked="${v.valores().usaModelo()}" disabled="${!v.modeloDisponible()}">
          Que el modelo local redacte las preguntas (el código las elige igual)</label>
        <button type="submit" class="boton" data-accion="empezar-sesion">Empezar</button>
      </form>
    </section>

    <section class="historial-sesiones" aria-labelledby="historial-titulo">
      <h2 id="historial-titulo">Tus sesiones</h2>
      @if(v.sesiones().isEmpty())
        <p class="detalle">Todavía no tienes sesiones.</p>
      @else
        <ul class="lista-sesiones">
          @for(VistaConsejero.Resumen r : v.sesiones())
            <li><a href="/consejero/sesiones/${r.id().toString()}">${r.postura()}</a>
              <span class="chip">${r.modo()}</span> <span class="chip ${"cerrada".equals(r.estado()) ? "chip-ok" : "chip-pendiente"}">${r.estado()}</span>
              <span class="detalle">${r.fecha()}</span></li>
          @endfor
        </ul>
      @endif
    </section>
  </section>
`)
