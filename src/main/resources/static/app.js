/* app.js: menos de 100 líneas. Sin inline ni eval (CSP script-src 'self').
   1) componentes Alpine (build CSP), 2) token CSRF renovado por el servidor, 3) progreso de subida,
   4) scroll al resultado en celular, 5) flechas en las pestañas, 6) espejo del campo entero,
   7) resultado anterior atenuado y marcas del ejemplo que se vuelven a pedir.
   El swap del 422 y del 401 lo configura <meta name="htmx-config">. */
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
    // Mapa (V01): un clic en un nodo del SVG o en la lista lo resalta en ambos; la lista se recorre con teclado.
    window.Alpine.data('mapa', function () {
      return {
        elegir: function (evento) {
          var origen = evento.target.closest('[data-nodo], g.node');
          if (!origen) { return; }
          var id = origen.getAttribute('data-nodo') || origen.id;
          this.$el.querySelectorAll('g.node').forEach(function (g) { g.classList.toggle('seleccionado', g.id === id); });
          this.$el.querySelectorAll('[data-nodo]').forEach(function (b) {
            b.setAttribute('aria-pressed', b.getAttribute('data-nodo') === id ? 'true' : 'false');
          });
        }
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
    var destino = evento.detail && evento.detail.target;
    var resultado = destino && (destino.id && destino.id.indexOf('res-') === 0 ? destino : destino.querySelector('[id^="res-"]'));
    if (resultado && window.innerWidth < 800) {
      resultado.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  });

  // 5) Pestañas con teclado (ARIA tablist): flechas izquierda y derecha mueven el foco entre pestañas.
  document.body.addEventListener('keydown', function (evento) {
    var tab = evento.target.closest && evento.target.closest('[role="tab"]');
    if (!tab || (evento.key !== 'ArrowRight' && evento.key !== 'ArrowLeft')) { return; }
    var tabs = Array.prototype.slice.call(tab.parentNode.querySelectorAll('[role="tab"]'));
    var i = tabs.indexOf(tab) + (evento.key === 'ArrowRight' ? 1 : -1);
    tabs[(i + tabs.length) % tabs.length].focus();
    evento.preventDefault();
  });

  // 6) Campo entero: el control de rango y el número muestran el mismo valor.
  document.body.addEventListener('input', function (evento) {
    var campo = evento.target;
    var espejo = campo.dataset && campo.dataset.espejo ? document.getElementById(campo.dataset.espejo)
      : document.querySelector('[data-espejo="' + campo.id + '"]');
    if (!espejo) { return; }
    espejo.value = campo.value;
    var salida = campo.closest('.entero-con-rango') && campo.closest('.entero-con-rango').querySelector('output');
    if (salida) { salida.textContent = campo.value; }
  });
  // 7) Un cambio atenúa el resultado anterior; con datos de un ejemplo, cambiar el texto de una fila borra sus marcas o su columna.
  function aviso(clase, texto) { var p = document.createElement('p'); p.className = clase; p.textContent = texto; return p; }
  document.body.addEventListener('input', function (evento) {
    var campo = evento.target, form = campo.closest && campo.closest('form.formulario-tecnica'); if (!form) { return; }
    var t = form.id.replace('form-', ''), zona = document.getElementById('resultado-' + t), estado = document.getElementById('estado-' + t);
    if (estado) { estado.textContent = ''; }
    if (zona && zona.querySelector('[data-patron]') && !zona.classList.contains('desactualizado')) { zona.classList.add('desactualizado');
      zona.insertBefore(aviso('aviso-desactualizado', 'Resultado anterior: cambiaste tus datos. Vuelve a evaluar para ver el de ahora.'), zona.firstChild); }
    var fila = form.hasAttribute('data-desde-ejemplo') && campo.matches('input[type="text"], textarea') && campo.closest('li.fila');
    if (!fila || fila.dataset.revisar) { return; } fila.dataset.revisar = 'si';
    var propias = fila.querySelectorAll('input[type="radio"], select'), columna = '[name$="[' + fila.id.split('-fila-').pop() + ']"]';
    var marcas = propias.length ? propias : form.querySelectorAll('input[type="radio"]' + columna);
    marcas.forEach(function (x) { if (x.tagName === 'SELECT') { x.value = ''; } else { x.checked = false; } });
    fila.appendChild(aviso('aviso-revisar', 'Cambiaste el texto: vuelve a marcar, las marcas del ejemplo se borraron.'));
  });
  document.body.addEventListener('htmx:afterSwap', function (e) { var d = e.detail.target; if (d && d.classList) { d.classList.remove('desactualizado'); } });
})();
