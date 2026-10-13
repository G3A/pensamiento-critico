@param Integer status = null
<%-- Vista "error" de Spring Boot (hito 8): sin detalles internos, sin traza ni mensaje; reemplaza a la página whitelabel. --%>
<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${status != null && status == 403 ? "Sin permiso" : status != null && status == 404 ? "No encontrada" : "Algo salió mal"} · Taller de Pensamiento Crítico</title>
  <link rel="stylesheet" href="/app.css">
  <link rel="icon" href="/favicon.svg" type="image/svg+xml">
</head>
<body>
<main id="principal" class="principal">
  <section class="bloqueo-pagina">
    <div class="tarjeta formulario-bloqueo">
      @if(status != null && status == 403)
        <h1>No puedes ver esta página</h1>
        <p>Tu cuenta no tiene permiso para entrar aquí. Si entraste por la dirección de otra institución, sal primero.</p>
      @elseif(status != null && status == 404)
        <h1>Esta página no existe</h1>
        <p>Revisa la dirección.</p>
      @else
        <h1>Algo salió mal</h1>
        <p>No pudimos completar lo que pediste. Vuelve a intentarlo en un momento.</p>
      @endif
      <p><a href="/">Volver al inicio</a></p>
    </div>
  </section>
</main>
</body>
</html>
