// RNF-03 · Concurrencia en red local: 10 personas navegando a la vez por el catálogo, la ficha de T28, su pestaña
// Usar y el Taller de argumentos (evaluar dibuja el mapa con Graphviz). Riesgo que se mide: que las pantallas sin
// IA pasen de 500 ms en p95 o fallen con 10 sesiones concurrentes. Modelo cerrado (constant-vus) a propósito: son
// personas que esperan cada pantalla y la leen antes de seguir.
// Hito 3: una persona más tiene una sesión con Ollama activa (RNF-03): pide al modelo un steelman en T34 y espera el
// evento final por SSE, mientras las otras diez navegan. El p95 que se exige sigue siendo el de las pantallas sin IA.
// Hito 4: cada persona abre además el tablero del Diario y evalúa T43 · Diagrama de Ishikawa (Graphviz dibuja la espina).
// Hito 5: cada persona abre el Consejero y evalúa T09 · 5 porqués (Graphviz dibuja la cadena); la sesión con Ollama activa
// pasa a ser una sesión del Consejero socrático: cada turno del Consejero llega por SSE hasta el evento final.
// Hito 6: cada persona abre la biblioteca y busca por palabras; una persona más importa documentos sin parar (trabajo
// largo: troceado e incrustaciones con bge-m3 en segundo plano) y espera a verlos indexados. El p95 exigido sigue siendo el
// de las pantallas sin IA: la importación no debe empujarlo.
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
    importacion: { executor: 'constant-vus', vus: 1, duration: __ENV.DURACION || '60s', gracefulStop: '180s', exec: 'importacion' },
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
  for (let i = 1; i <= PERSONAS + 2; i++) {
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
  sleep(1);

  // Hito 5: el Consejero (historial y sesión nueva) y T09 · 5 porqués con la cadena dibujada.
  const consejero = http.get(`${BASE}/consejero`, pantalla);
  check(consejero, { 'Consejero: historial y sesión nueva': (r) => r.status === 200 && r.body.includes('id="form-nueva-sesion"') });
  const porques = http.post(`${BASE}/tecnicas/T09/evaluar`, PORQUES,
    Object.assign({ headers: { 'X-CSRF-TOKEN': csrf, 'HX-Request': 'true' } }, pantalla));
  check(porques, { 'T09 evaluada con la cadena dibujada': (r) => r.status === 200 && r.body.includes('data-patron="V06"') && r.body.includes('<svg') });
  sleep(1);

  // Hito 6: la biblioteca (P13) y una búsqueda por palabras.
  const biblioteca = http.get(`${BASE}/biblioteca`, pantalla);
  check(biblioteca, { 'Biblioteca con su formulario de subida': (r) => r.status === 200 && r.body.includes('id="form-subida"') });
  const busqueda = http.get(`${BASE}/biblioteca/buscar?q=personas+por+hora&palabras=true`, Object.assign({ headers: { 'HX-Request': 'true' } }, pantalla));
  check(busqueda, { 'Biblioteca: búsqueda por palabras': (r) => r.status === 200 && r.body.includes('id="resultados-busqueda"') });
  sleep(2);
}

/** Un conteo ficticio de unos 25 KB (unos 30 fragmentos para bge-m3): único por iteración para que el hash no lo dé por repetido. */
function documento(n) {
  const parrafos = [];
  for (let i = 1; i <= 120; i++) {
    parrafos.push(`Conteo ${i} del barrio ${n}: entre las ${6 + (i % 12)} y las ${7 + (i % 12)} pasaron ${200 + i * 7} personas frente a la panadería; `
      + `el día estuvo ${i % 3 === 0 ? 'lluvioso' : 'despejado'} y la junta de vecinos anotó ${i % 5} eventos en la cuadra.`);
  }
  return `# Conteo peatonal ${n}\n\n${parrafos.join('\n\n')}\n`;
}

/** La importación como trabajo largo: subir un Markdown y esperar, como la lista que se refresca sola, a verlo indexado. */
export function importacion(datos) {
  if (csrf === null) {
    csrf = entrar(datos.nombres[PERSONAS + 1], '4826');
  }
  const largo = { tags: { pantalla: 'trabajo_largo' } };
  const nombre = `conteo-${__VU}-${__ITER}-${Date.now().toString(36)}.md`;
  const subida = http.post(`${BASE}/biblioteca`, { archivo: http.file(documento(nombre), nombre, 'text/markdown') },
    Object.assign({ headers: { 'X-CSRF-TOKEN': csrf, 'HX-Request': 'true' } }, largo));
  check(subida, { 'Biblioteca: el documento se importa': (r) => r.status === 200 && r.body.includes(`Importado: ${nombre}`) });
  const estado = new RegExp(`data-estado="([a-z_]+)">\\s*<span[^>]*>[^<]*</span>\\s*<strong class="nombre-documento">${nombre.replace(/[.]/g, '\\.')}<`);
  for (let i = 0; i < 120; i++) {
    const m = estado.exec(http.get(`${BASE}/biblioteca/lista`, Object.assign({ headers: { 'HX-Request': 'true' } }, largo)).body || '');
    if (m !== null && (m[1] === 'indexado' || m[1] === 'error')) {
      check(m[1], { 'Biblioteca: el documento queda indexado': (e) => e === 'indexado' });
      return;
    }
    sleep(1);
  }
  check(null, { 'Biblioteca: el documento queda indexado': () => false });
}

/** El pan quemado de T09 (docs/ejemplos/T09.md, ejemplo 1). */
const PORQUES = {
  'config.niveles': '5', 'config.exigirEvidencia': 'true', 'config.permitirRamas': 'false',
  problema: 'Se quemó la tanda de pan de las 6.',
  'porques[0].texto': 'El horno marcó 30 grados de más.', 'porques[0].evidencia': 'La pantalla del horno a las 6:10.',
  'porques[1].texto': 'El termostato está descalibrado.', 'porques[1].evidencia': 'El técnico lo midió con otro termómetro.',
  'porques[2].texto': 'Nadie lo revisa desde hace un año.',
  'porques[3].texto': 'No existe un calendario de mantenimiento.', 'porques[3].raiz': 'true',
};

/** El pan quemado de la sucursal original (docs/ejemplos/T43.md, ejemplo 1). */
const ISHIKAWA = {
  'config.categorias': 'Máquina, Método, Material, Personas, Medición, Entorno', 'config.causasMinimas': '1',
  efecto: 'Pan quemado en la sucursal original.',
  'causas[0].texto': 'El termostato marca de más', 'causas[0].categoria': 'Máquina',
  'causas[1].texto': 'No hay tiempos de horneado escritos', 'causas[1].categoria': 'Método',
  'causas[2].texto': 'Turno nuevo sin entrenar', 'causas[2].categoria': 'Personas',
};

const TURNO = /sse-connect="\/consejero\/turnos\/([0-9a-f-]{36})\/flujo"/;
const RESPUESTAS = [
  'Quiero vender más los fines de semana sin cansar al equipo.',
  'Doy por hecho que los domingos pasa gente por la panadería.',
  'Lo vi tres domingos seguidos desde la ventana de la casa.',
];

/** Lee por SSE el turno del Consejero que redacta el modelo, si la respuesta trae uno, hasta el evento final. */
function esperarTurno(cuerpo, conIa) {
  const m = TURNO.exec(cuerpo || '');
  if (m === null) {
    return false;
  }
  const flujo = http.get(`${BASE}/consejero/turnos/${m[1]}/flujo`, Object.assign({ timeout: '300s', headers: { Accept: 'text/event-stream' } }, conIa));
  check(flujo, { 'Consejero: el turno termina con el evento final': (r) => r.status === 200 && r.body.includes('event:fin') });
  return true;
}

/** La sesión con Ollama activa: una sesión del Consejero que pide el modelo; cada turno se lee por SSE hasta el final. */
export function sesionConOllama(datos) {
  if (csrf === null) {
    csrf = entrar(datos.nombres[PERSONAS], '4826');
  }
  const conIa = { tags: { pantalla: 'con_ia' } };
  const nueva = http.post(`${BASE}/consejero/sesiones`, { modo: 'decision', postura: 'Conviene abrir los domingos.', confianza: '70', usaModelo: 'on' },
    Object.assign({ headers: { 'X-CSRF-TOKEN': csrf }, redirects: 0 }, conIa));
  const destino = (nueva.headers.Location || '').replace(/^https?:\/\/[^/]+/, '');
  check(nueva, { 'Consejero: la sesión empieza': (r) => r.status === 303 && destino.startsWith('/consejero/sesiones/') });
  if (!destino.startsWith('/consejero/sesiones/')) {
    sleep(5);
    return;
  }
  const sesion = http.get(`${BASE}${destino}`, conIa);
  check(sesion, { 'Consejero: la sesión muestra el diálogo': (r) => r.status === 200 && r.body.includes('id="dialogo"') });
  esperarTurno(sesion.body, conIa);
  for (const texto of RESPUESTAS) {
    const turno = http.post(`${BASE}${destino}/turnos`, { texto: texto },
      Object.assign({ headers: { 'X-CSRF-TOKEN': csrf, 'HX-Request': 'true' } }, conIa));
    check(turno, { 'Consejero: la respuesta trae el turno siguiente': (r) => r.status === 200 && r.body.includes('burbuja-consejero') });
    esperarTurno(turno.body, conIa);
    sleep(1);
  }
}
