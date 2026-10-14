@import pensamiento.web.Pagina
@import java.util.List
@param Pagina pagina
@param List<String> codigos
@param String inicio
<%-- Códigos de recuperación de la llave de acceso (hito 9): se muestran una sola vez; en la base solo queda su hash. --%>
@template.layout(pagina = pagina, contenido = @`
  <section class="tarjeta-codigo-pagina">
    <article class="tarjeta tarjeta-codigo" aria-labelledby="codigos-titulo">
      <h1 id="codigos-titulo">Tus códigos de recuperación</h1>
      <p>Tu llave quedó registrada. Si un día pierdes este equipo, entra con tu contraseña y uno de estos códigos. Cada uno
        sirve <strong>una sola vez</strong>. Imprímelos o cópialos en papel ahora: no se vuelven a mostrar.</p>
      <ol class="codigos-recuperacion" translate="no">
        @for(String c : codigos)
          <li>${c}</li>
        @endfor
      </ol>
    </article>
    <p class="no-imprimir"><a class="boton" href="${inicio}">Ya los guardé: seguir</a></p>
  </section>
`)
