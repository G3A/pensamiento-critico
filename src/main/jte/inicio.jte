@import pensamiento.web.Pagina
@import pensamiento.web.ControladorInicio
@import pensamiento.catalogo.Intencion
@import pensamiento.nucleo.PendienteGuardado
@import pensamiento.nucleo.Expediente
@param Pagina pagina
@param ControladorInicio.VistaInicio v
@template.layout(pagina = pagina, contenido = @`
  <section class="inicio" aria-labelledby="inicio-titulo">
    <h1 id="inicio-titulo">¿Qué quieres hacer hoy?</h1>
    <ul class="intenciones" aria-label="Intenciones">
      @for(Intencion intencion : v.intenciones())
        <li><a class="tarjeta intencion" href="/catalogo?intencion=${intencion.clave()}" data-intencion="${intencion.clave()}">${intencion.titulo()}</a></li>
      @endfor
    </ul>

    @if(v.vacio())
      <div class="tarjeta vacio primer-uso">
        <p><strong>Todavía no tienes nada guardado.</strong></p>
        <p>Mira cómo se ve un asunto terminado:
          <a href="/expedientes/muestra" id="enlace-muestra">expediente de muestra «${v.nombreMuestra()}»</a>
          <span class="chip chip-pendiente">solo lectura</span></p>
        <p class="acciones">
          <a class="boton" id="empieza-con-un-ejemplo" href="${v.primerEjemplo()}">Empieza con un ejemplo</a>
          <a class="boton boton-secundario" href="/catalogo">Ver el catálogo</a>
        </p>
        <p class="detalle">"Empieza con un ejemplo" abre T28 · Análisis de hipótesis en competencia (ACH) con el caso de la panadería ya cargado: solo tienes que evaluar.</p>
      </div>
    @else
      <div class="inicio-columnas">
        <section aria-labelledby="pendientes-titulo">
          <h2 id="pendientes-titulo">Pendientes</h2>
          @if(!v.revisiones().isEmpty())
            <ul class="pendientes revisiones-diario" aria-label="Revisiones del Diario">
              @for(var d : v.revisiones())
                <li data-prediccion="${d.prediccion().id().toString()}"><span class="chip chip-aviso">revisión</span>
                  <a href="/diario#revisiones">Revisar la decisión: ${d.decision()}</a> <span class="detalle">· tocaba el ${d.revisarEl()}</span></li>
              @endfor
            </ul>
          @endif
          @if(v.retosDojo() > 0)
            <ul class="pendientes repasos-dojo" aria-label="Repasos del Dojo">
              <li data-dojo="${v.retosDojo()}"><span class="chip chip-aviso">repaso</span>
                <a href="/dojo">B · ${v.retosDojo()} ${v.retosDojo() == 1 ? "reto" : "retos"} del Dojo para hoy</a>
                <span class="detalle">· racha ${v.rachaDojo()}</span></li>
            </ul>
          @endif
          @if(v.pendientes().isEmpty() && v.revisiones().isEmpty() && v.retosDojo() == 0)
            <p class="detalle">Nada pendiente.</p>
          @else
            <ul class="pendientes">
              @for(PendienteGuardado p : v.pendientes())
                <li><span class="chip chip-aviso">${p.pendiente().tipo().name().toLowerCase()}</span>
                  <a href="${pensamiento.web.EnlacePendiente.de(p)}">${p.pendiente().descripcion()}</a></li>
              @endfor
            </ul>
          @endif
        </section>
        <section aria-labelledby="recientes-titulo">
          <h2 id="recientes-titulo">Recientes</h2>
          <ul class="recientes">
            @for(ControladorInicio.Reciente r : v.recientes())
              <li><span class="chip">${r.tecnica().familia()}</span> <a href="/ejecuciones/${r.ejecucion().id().toString()}">${r.ejecucion().resumen()}</a>
                <span class="detalle">${r.tecnica().cita()} · ${r.fecha()}</span></li>
            @endfor
          </ul>
        </section>
        <section aria-labelledby="mis-expedientes-titulo">
          <h2 id="mis-expedientes-titulo">Expedientes</h2>
          <ul>
            @for(Expediente x : v.expedientes())
              <li><a href="/expedientes/${x.id().toString()}">${x.nombre()}</a></li>
            @endfor
          </ul>
          <p><a href="/expedientes">Todos los expedientes</a> · <a href="/expedientes/muestra">expediente de muestra</a></p>
        </section>
      </div>
      <p class="acciones"><a class="boton boton-secundario" href="${v.primerEjemplo()}">Empieza con un ejemplo</a></p>
    @endif

    @if(pagina.modoPlantillas())
      <div class="tarjeta aviso" role="note">
        <p><span class="chip chip-aviso">sin IA local</span> Ollama no está disponible en esta máquina: todas las técnicas funcionan en modo plantillas.</p>
        <details>
          <summary>Qué cambia</summary>
          <p>Nada que esté en el catálogo deja de funcionar. Las técnicas marcadas "Ollama opcional" piden que escribas tú lo que el modelo propondría (steelman, posturas opuestas, clasificación de falacias). Lo que viene del modelo nunca cuenta hasta que tú lo adoptas, así que el modo plantillas es el modo normal, no una degradación.</p>
        </details>
      </div>
    @endif
  </section>
`)
