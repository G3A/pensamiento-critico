@import pensamiento.web.formulario.VistaCampo
@param VistaCampo c
@if(c.tieneAyuda())
  <p class="ayuda" id="${c.idAyuda()}">${c.ayuda()}</p>
@endif
@if(c.tieneError())
  <p class="error-campo" id="${c.idError()}" role="alert"><span class="chip chip-error">error</span> ${c.error()}</p>
@endif
