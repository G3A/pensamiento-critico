@import pensamiento.web.consejero.VistaConsejero
@import java.util.List
@param VistaConsejero.Sesion v
@param List<VistaConsejero.Burbuja> nuevas
@for(VistaConsejero.Burbuja b : nuevas)
  @template.fragmentos.consejero.burbuja(b = b)
@endfor
@template.fragmentos.consejero.panel(v = v, oob = true)
@template.fragmentos.consejero.entrada(v = v, oob = true)
<p id="mensaje-consejero" class="error" role="alert" hx-swap-oob="true"></p>
