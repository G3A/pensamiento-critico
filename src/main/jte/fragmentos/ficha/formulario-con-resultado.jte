@import gg.jte.Content
@import pensamiento.web.tecnicas.VistaFormulario
@param VistaFormulario f
@param Content resultadoOob
@template.fragmentos.ficha.formulario(f = f, oob = false)
@if(resultadoOob != null)
  <div id="${f.idResultado()}" class="zona-resultado" aria-live="polite" hx-swap-oob="true">
    ${resultadoOob}
    <p class="detalle sin-guardar"><span class="chip">sin guardar</span> Adoptaste una propuesta del modelo: ahora cuenta. Usa "Guardar en historial" para conservarlo.</p>
  </div>
@endif
