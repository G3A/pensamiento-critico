// RNF-03 · Concurrencia en red local: 10 personas navegando a la vez por el catálogo, la ficha de T28, su pestaña
// Usar y el Taller de argumentos (evaluar dibuja el mapa con Graphviz). Riesgo que se mide: que las pantallas sin
// IA pasen de 500 ms en p95 o fallen con 10 sesiones concurrentes. Modelo cerrado (constant-vus) a propósito: son
// personas que esperan cada pantalla y la leen antes de seguir.
// Hito 3: una persona más tiene una sesión con Ollama activa (RNF-03): pide al modelo un steelman en T34 y espera el
// evento final por SSE, mientras las otras diez navegan. El p95 que se exige sigue siendo el de las pantallas sin IA.
// Hito 4: cada persona abre además el tablero del Diario y evalúa T43 · Diagrama de Ishikawa (Graphviz dibuja la espina).
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
    sesion_con_ollama: { executor: 'constant-vus', vus: 1, duration: __ENV.DURACION || '60s', gracefulStop: '300s', exec: 'sesionConOllama' },
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
  for (let i = 1; i <= PERSONAS + 1; i++) {
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
  sleep(1);

  // Hito 4: el tablero del Diario (R05 sobre las predicciones de la persona) y T43 · Diagrama de Ishikawa con Graphviz.
  const diario = http.get(`${BASE}/diario`, pantalla);
  check(diario, { 'Diario: tablero con las revisiones al entrar': (r) => r.status === 200 && r.body.includes('id="revisiones"') });
  const ishikawa = http.post(`${BASE}/tecnicas/T43/evaluar`, ISHIKAWA,
    Object.assign({ headers: { 'X-CSRF-TOKEN': csrf, 'HX-Request': 'true' } }, pantalla));
  check(ishikawa, { 'T43 evaluada con la espina dibujada': (r) => r.status === 200 && r.body.includes('data-patron="V07"') && r.body.includes('<svg') });
  sleep(2);
}

/** El pan quemado de la sucursal original (docs/ejemplos/T43.md, ejemplo 1). */
const ISHIKAWA = {
  'config.categorias': 'Máquina, Método, Material, Personas, Medición, Entorno', 'config.causasMinimas': '1',
  efecto: 'Pan quemado en la sucursal original.',
  'causas[0].texto': 'El termostato marca de más', 'causas[0].categoria': 'Máquina',
  'causas[1].texto': 'No hay tiempos de horneado escritos', 'causas[1].categoria': 'Método',
  'causas[2].texto': 'Turno nuevo sin entrenar', 'causas[2].categoria': 'Personas',
};

const TURNO = /sse-connect="\/ia\/turnos\/([0-9a-f-]{36})\/flujo"/;

/** La sesión con Ollama activa: pedir un steelman a T34 y leer el turno por SSE hasta el evento final. */
export function sesionConOllama(datos) {
  if (csrf === null) {
    csrf = entrar(datos.nombres[PERSONAS], '4826');
  }
  const conIa = { tags: { pantalla: 'con_ia' } };
  const pedido = http.post(`${BASE}/tecnicas/T34/propuestas`, {
    'config.longitudMaxima': '120', 'config.modo': 'manual_y_modelo', 'config.exigirCita': 'true',
    posturaOriginal: 'Los que no quieren camaras no les importa el barrio.',
    cita: 'Prefiero que no me graben cada vez que salgo de mi casa.',
    _clave: `k6-${__VU}-${__ITER}`, _origen: 'tu configuracion',
  }, Object.assign({ headers: { 'X-CSRF-TOKEN': csrf, 'HX-Request': 'true' } }, conIa));
  const m = TURNO.exec(pedido.body || '');
  check(pedido, { 'T34: pedir abre un turno con el modelo': (r) => r.status === 200 && m !== null });
  if (m === null) {
    sleep(5);
    return;
  }
  const flujo = http.get(`${BASE}/ia/turnos/${m[1]}/flujo`, Object.assign({ timeout: '300s', headers: { Accept: 'text/event-stream' } }, conIa));
  check(flujo, { 'T34: el turno termina con el evento final': (r) => r.status === 200 && r.body.includes('event:fin') });
  sleep(2);
}
