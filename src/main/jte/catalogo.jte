@import pensamiento.web.Pagina
@import pensamiento.nucleo.Familia
@import pensamiento.nucleo.Tecnica
@import java.util.List
@param Pagina pagina
@param List<Familia> familias
@param List<Tecnica> tecnicas
@param String familiaActiva
@param String intencionActiva
@param long total
@template.layout(pagina = pagina, contenido = @`
  <section class="catalogo">
    <h1>Catálogo · ${total} técnicas en 8 familias</h1>
    <div class="catalogo-cuerpo">
      <nav class="familias" aria-label="Familias">
        <ul>
          <li><a href="/catalogo" hx-get="/catalogo" hx-target="#rejilla" hx-push-url="true" class="${familiaActiva.isEmpty() ? "activo" : ""}">Todas <span class="conteo">${total}</span></a></li>
          @for(Familia familia : familias)
            <li><a href="/catalogo?familia=${familia.codigo()}" hx-get="/catalogo?familia=${familia.codigo()}" hx-target="#rejilla" hx-push-url="true" class="${familiaActiva.equals(familia.codigo()) ? "activo" : ""}">${familia.codigo()} ${familia.nombre()}</a></li>
          @endfor
        </ul>
      </nav>
      <div id="rejilla">
        @template.fragmentos.rejilla-catalogo(tecnicas = tecnicas, familiaActiva = familiaActiva, intencionActiva = intencionActiva)
      </div>
    </div>
  </section>
`)
