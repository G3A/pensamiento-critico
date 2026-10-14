@import pensamiento.web.Pagina
@param Pagina pagina
<%-- "Reto de hoy" sin tema asignado (hito 10): invita al Dojo, que sigue siendo de cada estudiante. --%>
@template.layout(pagina = pagina, contenido = @`
  <section class="reto" aria-labelledby="reto-titulo">
    <h1 id="reto-titulo">Reto de hoy</h1>
    <div class="tarjeta vacio">
      <p>Tu profe todavía no eligió un tema para tu grupo. Mientras tanto, practica en el Dojo: falacias, sesgos, datos y fuentes,
        con tus repasos de siempre.</p>
      <p><a class="boton" href="/dojo">Ir al Dojo</a></p>
    </div>
  </section>
`)
