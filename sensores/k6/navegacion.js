// RNF-03 · Concurrencia en red local: 10 personas navegando a la vez por el catálogo, la ficha de T28, su pestaña
// Usar y el Taller de argumentos (evaluar dibuja el mapa con Graphviz). Riesgo que se mide: que las pantallas sin
// IA pasen de 500 ms en p95 o fallen con 10 sesiones concurrentes. Modelo cerrado (constant-vus) a propósito: son
// personas que esperan cada pantalla y la leen antes de seguir.
// Se corre con: docker compose --profile test run --rm k6
import http from 'k6/http';
import { check, sleep, fail } from 'k6';

const BASE = __ENV.APP_URL || 'http://app:8080';
const PIN_ADMIN = open('/run/secrets/admin_pin').trim();
const PERSONAS = 10;
const META_CSRF = /<meta name="_csrf" content="([^"]+)"/;

const SUCURSAL = [
  '[Sucursal]: Conviene abrir la segunda sucursal en el centro.',
  '  + <Más ventas>: Con más gente pasando, se vende más. {peso: 3}',
  '    + [Tráfico]: El centro tiene más tráfico peatonal que el barrio. #asumible',
  '    + [Conversión]: Más tráfico da más ventas. #asumible',
  '  - [Personal]: Falta personal para atender dos locales.',
].join('\n');

export const options = {
  // Cada usuario virtual es una persona con su sesión: sin esto k6 borra las cookies entre iteraciones.
  noCookiesReset: true,
  scenarios: {
    navegacion: { executor: 'constant-vus', vus: PERSONAS, duration: __ENV.DURACION || '60s' },
  },
  thresholds: {
    'http_req_duration{pantalla:sin_ia}': ['p(95)<500'],
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
  },
};

function csrfDe(cuerpo) {
  const m = META_CSRF.exec(cuerpo || '');
  return m ? m[1] : '';
}

/** Entra con nombre y PIN; devuelve el token CSRF de la sesión ya autenticada. */
function entrar(nombre, pin) {
  const bloqueo = http.get(`${BASE}/bloqueo`);
  const r = http.post(`${BASE}/sesion`, { nombre: nombre, pin: pin, volver: '/' },
    { headers: { 'X-CSRF-TOKEN': csrfDe(bloqueo.body) } });
  if (r.status !== 200) {
    fail(`${nombre} no pudo entrar (estado ${r.status})`);
  }
  return csrfDe(http.get(`${BASE}/`).body);
}

/** El administrador crea diez personas de prueba, una por usuario virtual. */
export function setup() {
  const csrf = entrar('administrador', PIN_ADMIN);
  const sufijo = Date.now().toString(36);
  const nombres = [];
  for (let i = 1; i <= PERSONAS; i++) {
    const nombre = `vecino k6 ${sufijo} ${i}`;
    const r = http.post(`${BASE}/usuarios`, { nombre: nombre, pin: '4826' }, { headers: { 'X-CSRF-TOKEN': csrf } });
    if (r.status !== 200) {
      fail(`no se pudo crear ${nombre} (estado ${r.status})`);
    }
    nombres.push(nombre);
  }
  return { nombres: nombres };
}

let csrf = null;

export default function (datos) {
  if (csrf === null) {
    csrf = entrar(datos.nombres[(__VU - 1) % PERSONAS], '4826');
  }
  const pantalla = { tags: { pantalla: 'sin_ia' } };

  const catalogo = http.get(`${BASE}/catalogo`, pantalla);
  check(catalogo, { 'catálogo: 49 técnicas': (r) => r.status === 200 && (r.body.match(/id="tecnica-T/g) || []).length === 49 });
  sleep(1);

  const ficha = http.get(`${BASE}/tecnicas/T28`, pantalla);
  check(ficha, { 'ficha de T28': (r) => r.status === 200 && r.body.includes('role="tablist"') });
  sleep(1);

  const usar = http.get(`${BASE}/tecnicas/T28?pestana=usar`, pantalla);
  check(usar, { 'Usar de T28 con su formulario': (r) => r.status === 200 && r.body.includes('id="form-T28"') });
  sleep(1);

  const taller = http.get(`${BASE}/taller`, pantalla);
  check(taller, { 'Taller de argumentos': (r) => r.status === 200 && r.body.includes('id="form-taller"') });

  const evaluado = http.post(`${BASE}/taller/evaluar`, { argdown: SUCURSAL, estandar: 'preponderancia', textoEvaluado: '' },
    Object.assign({ headers: { 'X-CSRF-TOKEN': csrf, 'HX-Request': 'true' } }, pantalla));
  check(evaluado, { 'Taller evaluado con el mapa dibujado': (r) => r.status === 200 && r.body.includes('data-patron="V01"') && r.body.includes('<svg') });
  sleep(2);
}
