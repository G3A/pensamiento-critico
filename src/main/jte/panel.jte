@import pensamiento.web.Pagina
@import pensamiento.web.aula.ControladorPanel.VistaPanel
@param Pagina pagina
@param VistaPanel v
<%-- Panel del grupo (hito 10, entregable 7). --%>
@template.layout(pagina = pagina, contenido = @`
  <section class="panel" aria-labelledby="panel-titulo">
    <p class="migas"><a href="/grupos/${v.panel().grupo().id().toString()}">${v.panel().grupo().nombre()}</a></p>
    <h1 id="panel-titulo">Panel de ${v.panel().grupo().nombre()}</h1>
    <p class="detalle">Solo números: cuántos entraron y cuántos aciertos hubo. Lo que escribe cada estudiante es solo suyo y aquí no
      aparece nunca.@if(v.panel().clase().isPresent()) Clase abierta: se actualiza solo cada 30 segundos.@endif</p>
    @template.fragmentos.panel(v = v)
  </section>
`)
