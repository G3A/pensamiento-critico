/* llave.js (hito 9): registrar y confirmar la llave de acceso (WebAuthn) en /llave y /operador/llave.
   Archivo propio y no en línea porque la CSP es script-src 'self'. El servidor (Spring Security 7) arma las opciones y
   verifica la respuesta; aquí solo se pasan de base64url a bytes y de vuelta, como pide navigator.credentials. */
(function () {
  'use strict';
  var caja = document.querySelector('[data-llave]');
  if (!caja) { return; }
  var base = caja.getAttribute('data-base');
  var estado = caja.querySelector('[data-llave-estado]');

  function aBytes(texto) {
    var b64 = texto.replace(/-/g, '+').replace(/_/g, '/');
    var binario = atob(b64 + '==='.slice((b64.length + 3) % 4));
    return Uint8Array.from(binario, function (c) { return c.charCodeAt(0); });
  }
  function aTexto(bytes) {
    var binario = String.fromCharCode.apply(null, new Uint8Array(bytes));
    return btoa(binario).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  }
  function avisar(texto) { estado.textContent = texto; estado.hidden = !texto; }
  function pedir(ruta, cuerpo) {
    var meta = document.querySelector('meta[name="_csrf"]');
    return fetch(base + ruta, {
      method: 'POST', credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': meta ? meta.getAttribute('content') : '' },
      body: cuerpo ? JSON.stringify(cuerpo) : null
    }).then(function (r) {
      return r.json().catch(function () { return {}; }).then(function (datos) {
        if (!r.ok) { throw new Error(datos.error || 'No se pudo completar. Vuelve a intentarlo.'); }
        return datos;
      });
    });
  }
  function credenciales(lista) {
    return (lista || []).map(function (c) { return Object.assign({}, c, { id: aBytes(c.id) }); });
  }
  function fallo(error) {
    avisar(error && error.name === 'NotAllowedError' ? 'Se canceló o se venció el tiempo. Toca el botón otra vez.' : (error.message || String(error)));
  }

  function registrar() {
    avisar('');
    pedir('/registro/opciones').then(function (o) {
      o.challenge = aBytes(o.challenge);
      o.user.id = aBytes(o.user.id);
      o.excludeCredentials = credenciales(o.excludeCredentials);
      return navigator.credentials.create({ publicKey: o });
    }).then(function (c) {
      return pedir('/registro', { label: navigator.platform || 'Llave de acceso', credential: {
        id: c.id, rawId: aTexto(c.rawId), type: c.type, authenticatorAttachment: c.authenticatorAttachment || null,
        clientExtensionResults: c.getClientExtensionResults(),
        response: { attestationObject: aTexto(c.response.attestationObject), clientDataJSON: aTexto(c.response.clientDataJSON),
          transports: c.response.getTransports ? c.response.getTransports() : [] } } });
    }).then(function (r) { window.location.assign(r.ir); }).catch(fallo);
  }

  function confirmar() {
    avisar('');
    pedir('/confirmacion/opciones').then(function (o) {
      o.challenge = aBytes(o.challenge);
      o.allowCredentials = credenciales(o.allowCredentials);
      return navigator.credentials.get({ publicKey: o });
    }).then(function (c) {
      return pedir('/confirmacion', { id: c.id, rawId: aTexto(c.rawId), type: c.type,
        authenticatorAttachment: c.authenticatorAttachment || null, clientExtensionResults: c.getClientExtensionResults(),
        response: { authenticatorData: aTexto(c.response.authenticatorData), clientDataJSON: aTexto(c.response.clientDataJSON),
          signature: aTexto(c.response.signature), userHandle: c.response.userHandle ? aTexto(c.response.userHandle) : null } });
    }).then(function (r) { window.location.assign(r.ir); }).catch(fallo);
  }

  if (!window.PublicKeyCredential) { avisar('Este navegador no admite llaves de acceso. Usa uno actualizado.'); return; }
  caja.querySelectorAll('[data-llave-registrar]').forEach(function (b) { b.addEventListener('click', registrar); });
  caja.querySelectorAll('[data-llave-confirmar]').forEach(function (b) { b.addEventListener('click', confirmar); });
})();
