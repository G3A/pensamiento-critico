@import pensamiento.web.Pagina
@import pensamiento.web.diario.ControladorDiario
@param Pagina pagina
@param ControladorDiario.VistaAsistente a
!{String id = a.tecnica().id().valor();}
@template.layout(pagina = pagina, contenido = @`
  <section class="diario-decision" aria-labelledby="decision-titulo" data-expediente="${a.expediente().id().toString()}">
    <p class="migas"><a href="/diario">Diario</a> › decisión</p>
    <h1 id="decision-titulo">${a.titulo()}</h1>
    @template.fragmentos.diario.pasos(a = a)

    <section class="tecnica-del-paso" aria-labelledby="tecnica-paso-titulo">
      <h2 id="tecnica-paso-titulo">Paso ${a.paso().numero()} · ${a.tecnica().cita()}</h2>
      <p class="detalle">${a.tecnica().usalaCuando()} <a href="/tecnicas/${id}">Qué es</a></p>
      @if(a.bloqueo() != null)
        <p class="aviso-lista aviso-aviso" role="alert"><span class="chip chip-aviso">bloqueado</span> ${a.bloqueo()}</p>
      @else
        <div class="usar-cuerpo">
          @template.fragmentos.ficha.formulario(f = a.formulario(), oob = false)
          <div id="${a.formulario().idEspera()}" class="zona-espera" aria-live="polite"></div>
          <div id="${a.formulario().idResultado()}" class="zona-resultado" aria-live="polite">
            @if(a.ultimo().isPresent())
              <p class="detalle"><span class="chip chip-ok">guardada</span> Lo último que guardaste en esta decisión:</p>
              ${a.ultimo().get()}
            @else
              <p class="detalle">El resultado aparece aquí al evaluar. "Guardar en historial" lo deja en esta decisión.</p>
            @endif
          </div>
        </div>
      @endif
    </section>

    <p class="acciones">
      @if(a.siguiente().isPresent())
        <a class="boton" href="${a.siguiente().get()}" data-accion="siguiente-paso">Continuar al paso ${a.paso().numero() + 1}</a>
      @endif
      @if("T43".equals(id) && a.ultima().isPresent())
        <a class="boton boton-secundario" href="/diario/decisiones/${a.expediente().id().toString()}?paso=0&tecnica=T42">Ver como árbol MECE</a>
      @endif
      <a href="/expedientes/${a.expediente().id().toString()}">Ver el expediente</a> · <a href="/diario">Volver al Diario</a>
    </p>
  </section>
`)
