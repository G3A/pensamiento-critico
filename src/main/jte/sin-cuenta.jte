@import pensamiento.web.Pagina
@import pensamiento.web.ControladorSesion.VistaSinCuenta
@import pensamiento.nucleo.Familia
@import pensamiento.nucleo.Tecnica
@param Pagina pagina
@param VistaSinCuenta v
<%-- "¿No tienes cuenta?" (RF-18, hito 9): a quién pedirla, en tres pasos, con el texto de cada institución, y el catálogo a la vista. --%>
@template.layout(pagina = pagina, contenido = @`
  <section class="sin-cuenta">
    <h1>¿No tienes cuenta?</h1>
    <ol class="pasos">
      <li>Pídele tu cuenta a <strong>${v.quienDaCuentas()}</strong>. Nadie se da de alta solo.</li>
      <li>Te dará una tarjeta con un <strong>código de un solo uso</strong> (8 letras y números).</li>
      <li>Vuelve aquí, toca <a href="/${v.ruta()}/codigo">«Tengo un código»</a>, escríbelo y elige tu contraseña.</li>
    </ol>
    <p><a class="boton boton-secundario" href="/${v.ruta()}">Volver a Entrar</a></p>
    <h2>Mientras tanto, mira el catálogo</h2>
    <p class="detalle">Estas son las ${v.tecnicas().size()} técnicas que podrás usar cuando tengas tu cuenta.</p>
    @for(Familia f : v.familias())
      <details class="familia-sin-cuenta">
        <summary>${f.codigo()} · ${f.nombre()}</summary>
        <ul>
          @for(Tecnica t : v.deFamilia(f.codigo()))
            <li><strong>${t.id().valor()} · ${t.nombre()}</strong>. <span class="detalle">${t.usalaCuando()}</span></li>
          @endfor
        </ul>
      </details>
    @endfor
  </section>
`)
