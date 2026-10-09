@import pensamiento.web.consejero.VistaConsejero
@import pensamiento.web.tecnicas.VistaFormulario
@import pensamiento.nucleo.Tecnica
@param VistaFormulario f
@param Tecnica t
@param VistaConsejero.Sesion v
<section class="tecnica-del-debate" aria-labelledby="tecnica-debate-titulo" data-tecnica="${t.id().valor()}">
  <h3 id="tecnica-debate-titulo">${t.cita()}</h3>
  <p class="detalle">${t.usalaCuando()} <a href="/tecnicas/${t.id().valor()}">Qué es</a></p>
  <div class="usar-cuerpo">
    @template.fragmentos.ficha.formulario(f = f, oob = false)
    <div id="${f.idEspera()}" class="zona-espera" aria-live="polite"></div>
    <div id="${f.idResultado()}" class="zona-resultado" aria-live="polite">
      <p class="detalle">El resultado aparece aquí al evaluar. "Guardar en historial" lo deja en el expediente de esta sesión.</p>
    </div>
  </div>
</section>
