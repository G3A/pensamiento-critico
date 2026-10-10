@import pensamiento.web.Pagina
@import pensamiento.web.dojo.ControladorDojo
@import pensamiento.web.patrones.V13b
@param Pagina pagina
@param ControladorDojo.VistaProgreso v
@template.layout(pagina = pagina, contenido = @`
  <section class="dojo dojo-progreso" aria-labelledby="progreso-titulo">
    <h1 id="progreso-titulo">Tu progreso en el Dojo</h1>
    <p class="estado-dojo"><span class="chip">racha ${v.racha()}</span> <a href="/dojo">Volver a los retos</a></p>
    @template.tag.v.v13c(v = v.calendario())
    <h2 class="subtitulo-pagina">Niveles por tema</h2>
    @for(V13b t : v.temas())
      @template.tag.v.v13b(v = t)
    @endfor
    <p class="detalle">Configura el límite de retos por día y la facilidad inicial en T49 · Repetición espaciada, y los niveles y los
      aciertos para dominar en T48 · Taxonomía de Bloom.
      <a href="/tecnicas/T49?pestana=usar">Abrir T49</a> · <a href="/tecnicas/T48?pestana=usar">Abrir T48</a></p>
  </section>
`)
