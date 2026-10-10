@import pensamiento.web.Pagina
@import pensamiento.web.dojo.ControladorDojo
@import pensamiento.nucleo.NivelBloom
@param Pagina pagina
@param ControladorDojo.VistaDojo v
@template.layout(pagina = pagina, contenido = @`
  <section class="dojo" aria-labelledby="dojo-titulo">
    <h1 id="dojo-titulo">Dojo de razonamiento</h1>
    <p class="detalle">Retos cortos de falacias, sesgos, datos y fuentes. Repasas cada concepto justo antes de olvidarlo
      (T49 · Repetición espaciada) y subes de nivel con tus aciertos (T48 · Taxonomía de Bloom). Todo se califica por reglas,
      sin el modelo.</p>
    <p class="estado-dojo" data-estado="dojo">
      <span class="chip">racha ${v.p().racha()}</span>
      Hoy: ${v.p().hechosHoy()} de ${v.p().limite()} retos · ${v.p().progreso()}
    </p>
    <nav class="temas-dojo" aria-label="Temas del Dojo">
      <ul>
        @for(ControladorDojo.Tema t : v.temas())
          <li><a href="${t.url()}" aria-current="${t.actual() ? "page" : null}" class="${t.actual() ? "activo" : ""}">${t.nombre()}</a></li>
        @endfor
        <li><a href="/dojo/progreso">Progreso</a></li>
      </ul>
    </nav>
    @if(v.p().avanceManual())
      <form method="get" action="/dojo" class="nivel-manual">
        <input type="hidden" name="tema" value="${v.claveTema()}">
        <label for="nivel-dojo">Nivel (avance manual)</label>
        <select id="nivel-dojo" name="nivel">
          @for(NivelBloom n : v.p().nivelesActivos())
            <option value="${n.toString()}" selected="${n == v.p().nivel()}">${n.toString()}</option>
          @endfor
        </select>
        <button type="submit" class="boton boton-secundario">Cambiar de nivel</button>
      </form>
    @endif
    @if(v.respuesta().isPresent())
      @template.fragmentos.dojo.respuesta(pagina = pagina, v = v)
    @else
      @template.fragmentos.dojo.reto(pagina = pagina, v = v)
    @endif
  </section>
`)
