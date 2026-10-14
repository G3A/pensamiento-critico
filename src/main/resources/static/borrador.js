// Hito 10 · El borrador no se pierde cuando el wifi se cae (entregable 8). Para cada formulario con data-borrador: copia en
// el navegador (solo de esta persona y de ese formulario) en cada cambio; copia en el servidor cada 20 s y al perder el foco;
// sin red, el envío espera con el aviso "sin conexión" y se reenvía al volver la red con la misma _clave (el servidor no lo
// guarda dos veces). Al enviarse bien se borran las dos copias; "Salir" borra todas las copias de este navegador.
(function () {
  'use strict';
  var cabeceras = {};
  try { cabeceras = JSON.parse(document.body.getAttribute('hx-headers') || '{}'); } catch (e) { cabeceras = {}; }
  var persona = cabeceras['X-Persona'];
  var SIN_RED = 'Sin conexión: tu texto está guardado aquí. Se envía solo cuando vuelva la red.';
  function llave(f) { return 'borrador:' + persona + ':' + f.dataset.borrador; }
  function campos(f) {
    var datos = {};
    f.querySelectorAll('textarea[name], input[name]').forEach(function (c) {
      if (/^(hidden|password|file|submit)$/.test(c.type) || c.name.charAt(0) === '_') { return; }
      if ((c.type === 'radio' || c.type === 'checkbox') && !c.checked) { return; }
      datos[c.name] = c.value;
    });
    return datos;
  }
  function local(f, datos) {
    try {
      if (datos) { localStorage.setItem(llave(f), JSON.stringify(datos)); } else { localStorage.removeItem(llave(f)); }
    } catch (e) { /* sin almacenamiento local: queda la copia del servidor */ }
  }
  function aviso(f, texto) {
    var p = f.querySelector('[data-aviso-borrador]');
    if (!p) {
      p = Object.assign(document.createElement('p'), { className: 'aviso' });
      p.setAttribute('data-aviso-borrador', '');
      p.setAttribute('role', 'status');
      f.prepend(p);
    }
    p.textContent = texto; p.hidden = !texto;
  }
  function enviar(url, cuerpo) {
    return fetch(url, { method: 'POST', credentials: 'same-origin', body: new URLSearchParams(cuerpo),
      headers: Object.assign({ 'Content-Type': 'application/x-www-form-urlencoded' }, cabeceras) });
  }
  function alServidor(f) {
    var escrito = f.dataset.escrito;
    if (!navigator.onLine || !escrito || f.dataset.guardado === escrito) { return; }
    enviar('/borradores', { clave: f.dataset.borrador, campos: JSON.stringify(campos(f)) })
      .then(function (r) { if (r.ok) { f.dataset.guardado = escrito; } }).catch(function () { /* se reintenta en 20 s */ });
  }
  function restaurar(f) {
    var guardado = null;
    try { guardado = JSON.parse(localStorage.getItem(llave(f)) || 'null'); } catch (e) { guardado = null; }
    if (!guardado) { return; }
    Object.keys(guardado).forEach(function (n) {
      f.querySelectorAll('[name="' + CSS.escape(n) + '"]').forEach(function (c) {
        if (c.type === 'radio' || c.type === 'checkbox') { c.checked = c.value === guardado[n]; } else if (!c.value) { c.value = guardado[n]; }
      });
    });
    aviso(f, 'Recuperamos lo que tenías escrito en este equipo.');
  }
  function cambio(e) { var f = e.currentTarget; f.dataset.escrito = String(Date.now()); local(f, campos(f)); }
  function preparar(f) {
    if (!persona || f.dataset.preparado) { return; }
    f.dataset.preparado = 'si';
    restaurar(f);
    f.addEventListener('input', cambio);
    f.addEventListener('change', cambio);
    f.addEventListener('focusout', function () { alServidor(f); });
  }
  function cada(selector, accion) { document.querySelectorAll(selector).forEach(accion); }
  function pendiente(f) { f.dataset.pendiente = 'si'; local(f, campos(f)); aviso(f, SIN_RED); }

  document.addEventListener('DOMContentLoaded', function () { cada('form[data-borrador]', preparar); });
  document.addEventListener('htmx:load', function () { cada('form[data-borrador]', preparar); });
  setInterval(function () { cada('form[data-borrador]', alServidor); }, 20000);
  document.addEventListener('submit', function (e) {
    var f = e.target;
    if (f.getAttribute('action') === '/salir') {
      try { Object.keys(localStorage).filter(function (k) { return k.indexOf('borrador:') === 0; }).forEach(function (k) { localStorage.removeItem(k); }); } catch (x) { /* nada que borrar */ }
      return;
    }
    if (!f.dataset.borrador || navigator.onLine) { return; }
    e.preventDefault(); e.stopImmediatePropagation();   // htmx no llega a mandarlo
    pendiente(f);
  }, true);
  document.addEventListener('htmx:sendError', function (e) {
    var f = e.detail.elt.closest && e.detail.elt.closest('form[data-borrador]');
    if (f) { pendiente(f); }
  });
  window.addEventListener('offline', function () { cada('form[data-borrador]', function (f) { aviso(f, SIN_RED); }); });
  window.addEventListener('online', function () {
    cada('form[data-borrador]', function (f) { aviso(f, ''); });
    cada('form[data-borrador][data-pendiente]', function (f) { delete f.dataset.pendiente; f.requestSubmit(); });
  });
  document.addEventListener('htmx:afterRequest', function (e) {
    var f = e.detail.elt;
    if (!f.dataset || !f.dataset.borrador || !e.detail.successful) { return; }
    local(f, null);
    enviar('/borradores/borrar', { clave: f.dataset.borrador }).catch(function () { /* la copia del servidor se reemplaza o se borra después */ });
  });
})();
