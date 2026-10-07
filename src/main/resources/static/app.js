/* app.js: menos de 100 líneas. Sin inline ni eval (CSP script-src 'self').
   1) componentes Alpine (build CSP), 2) token CSRF renovado por el servidor, 3) progreso de subida,
   4) scroll al resultado en celular. El swap del 422 y del 401 lo configura <meta name="htmx-config">. */
(function () {
  'use strict';

  // 1) Componentes Alpine registrados por nombre: el build CSP no evalúa expresiones.
  document.addEventListener('alpine:init', function () {
    window.Alpine.data('menu', function () {
      return {
        abierto: false,
        get abiertoTexto() { return this.abierto ? 'true' : 'false'; },
        get claseMenu() { return this.abierto ? 'menu abierto' : 'menu'; },
        alternar: function () { this.abierto = !this.abierto; }
      };
    });
  });

  // 2) Cuando la sesión se renueva (bloqueo por inactividad), el servidor manda el token nuevo.
  document.body.addEventListener('csrf-renovado', function (evento) {
    var token = evento.detail && evento.detail.token;
    if (!token) { return; }
    document.body.setAttribute('hx-headers', JSON.stringify({ 'X-CSRF-TOKEN': token }));
    var meta = document.querySelector('meta[name="_csrf"]');
    if (meta) { meta.setAttribute('content', token); }
    document.querySelectorAll('input[name="_csrf"]').forEach(function (campo) { campo.value = token; });
  });

  // 3) Progreso de subida de archivos (biblioteca): el servidor pone <progress data-progreso> junto al formulario.
  document.body.addEventListener('htmx:xhr:progress', function (evento) {
    var barra = evento.target.querySelector('progress[data-progreso]');
    if (barra && evento.detail.lengthComputable) {
      barra.max = evento.detail.total;
      barra.value = evento.detail.loaded;
    }
  });

  // 4) En celular, el resultado de una técnica se apila debajo del formulario: llevar la vista hasta él.
  document.body.addEventListener('htmx:afterSwap', function (evento) {
    var resultado = evento.detail && evento.detail.target;
    if (resultado && resultado.id && resultado.id.indexOf('res-') === 0 && window.innerWidth < 800) {
      resultado.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  });
})();
