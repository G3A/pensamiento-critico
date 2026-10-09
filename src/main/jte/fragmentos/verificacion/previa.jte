@import pensamiento.flujos.FichaDeVerificacion
@param FichaDeVerificacion.Previa p
@if(p.fuerza().isPresent())
  <p class="barra-puntaje"><label for="previa-fuerza">Fuerza de la evidencia ${p.fuerza().get()} de 8 (R01)</label>
    <meter id="previa-fuerza" min="0" max="8" value="${p.fuerza().get()}">${p.fuerza().get()} de 8</meter></p>
@endif
@if(p.puntajeCraap().isPresent())
  <p class="barra-puntaje"><label for="previa-craap">CRAAP ${p.puntajeCraap().get()} de 25</label>
    <meter id="previa-craap" min="0" max="25" value="${p.puntajeCraap().get()}">${p.puntajeCraap().get()} de 25</meter></p>
@endif
<p class="texto-previa" role="status">${p.texto()}</p>
