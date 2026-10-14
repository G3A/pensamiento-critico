@import pensamiento.web.seguridad.RespuestasSesion.ModeloEntrada
@param ModeloEntrada bloqueo
<%-- Overlay de sesión expirada en medio de un formulario (RF-02b): el borrador queda intacto debajo y solo la misma persona sigue. --%>
<div class="overlay" role="dialog" aria-modal="true" aria-labelledby="titulo-entrar">
  @template.tag.entrar(m = bloqueo)
</div>
