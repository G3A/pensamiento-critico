@import pensamiento.web.manual.ManualPorFamilia
@param ManualPorFamilia.Capitulo c
@param String html
@param String css
<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Manual · ${c.familia().codigo()} · ${c.familia().nombre()}</title>
  <style>$unsafe{css}</style>
</head>
<body>
  <main id="principal" class="principal manual">
    $unsafe{html}
  </main>
</body>
</html>
