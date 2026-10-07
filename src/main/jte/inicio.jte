@import pensamiento.web.Pagina
@import pensamiento.catalogo.Intencion
@param Pagina pagina
@param Intencion[] intenciones
@template.layout(pagina = pagina, contenido = @`
  <section class="inicio">
    <h1>¿Qué quieres hacer hoy?</h1>
    <ul class="intenciones">
      @for(Intencion intencion : intenciones)
        <li><a class="tarjeta intencion" href="/catalogo?intencion=${intencion.clave()}">${intencion.titulo()}</a></li>
      @endfor
    </ul>
    <div class="tarjeta vacio">
      <p><strong>Todavía no tienes nada guardado.</strong></p>
      <p>En el hito 1 verás aquí el expediente de muestra <em>La segunda sucursal de la panadería</em> y el botón "Empieza con un ejemplo" con T28 · Análisis de hipótesis en competencia (ACH).</p>
      <p><a class="boton" href="/catalogo">Ver el catálogo</a></p>
    </div>
    @if(pagina.modoPlantillas())
      <div class="tarjeta aviso" role="note">
        <p><span class="chip chip-aviso">sin IA local</span> Ollama no está disponible en esta máquina: todas las técnicas funcionan en modo plantillas.</p>
        <details>
          <summary>Qué cambia</summary>
          <p>Nada que esté en el catálogo deja de funcionar. Las técnicas marcadas "Ollama opcional" piden que escribas tú lo que el modelo propondría (steelman, sugerencias de pre-mortem, clasificación de falacias). Lo que viene del modelo nunca cuenta hasta que tú lo adoptas, así que el modo plantillas es el modo normal, no una degradación.</p>
        </details>
      </div>
    @endif
  </section>
`)
