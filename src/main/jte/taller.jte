@import pensamiento.web.Pagina
@import pensamiento.web.taller.VistaTaller
@param Pagina pagina
@param VistaTaller t
@template.layout(pagina = pagina, contenido = @`
  <section class="taller" aria-labelledby="taller-titulo">
    <h1 id="taller-titulo">Taller de argumentos</h1>
    <p class="detalle">Escribe tu argumento y marca cada línea como conclusión, premisa u objeción. El mapa se dibuja al evaluar, el panel
      Toulmin dice qué le falta y el panel de falacias señala qué preguntas críticas quedan sin responder.
      @if(t.argdown().isBlank())<a href="/taller?ejemplo=sucursal">Empieza con el ejemplo de la panadería</a>.@endif</p>
    @template.fragmentos.taller.formulario(t = t)
    <div id="taller-guardado" class="taller-guardado" aria-live="polite"></div>
    <aside class="tarjeta taller-todavia-no" aria-labelledby="todavia-no">
      <h2 id="todavia-no">Todavía no está en el Taller</h2>
      <p>La vista Wigmore del mismo mapa, el estándar de prueba por afirmación, la vista simple CER y el steelman de una objeción llegarán
        más adelante. Mientras tanto, el estándar se elige para todo el mapa.</p>
    </aside>
  </section>
`)
