// Hito 10, entregable 10 · Capacidad del aula sin Ollama: 140 sesiones a la vez (4 aulas de 35). Cada estudiante entra con su
// nombre y su contraseña (escalonados en los primeros 35 s, como llegan a clase), abre "Reto de hoy", responde el reto que
// toca, mira el Dojo y "Mis trabajos", y vuelve a empezar tras leer unos segundos. Quien administra mira el panel de los
// cuatro grupos cada 10 s, como un docente que lo tiene abierto. Riesgo que se mide: que alguna de esas pantallas pase de
// 500 ms en p95 o falle con 140 sesiones. La siembra es preparar-aula.sql (servicio k6-preparar).
// Se corre con: docker compose --profile test run --rm k6 run --summary-export=/resultados/aula.json /scripts/aula.js
import http from 'k6/http';
import { check, sleep, fail } from 'k6';

import { entrar, confirmarLlave } from './navegacion.js';

const BASE = __ENV.APP_URL || 'http://app-prueba:8080';
const ESTUDIANTES = 140;
const POR_GRUPO = 35;
const RETO = /data-reto="([a-z_]+-[iaec][0-9])"/;
const CLAVE = /name="_clave" value="([^"]+)"/;
const OPCION = /name="respuesta" value="([^"]+)"/;
const GRUPO = /href="\/grupos\/([0-9a-f-]{36})"/g;

export const options = {
  noCookiesReset: true,
  scenarios: {
    estudiantes: { executor: 'constant-vus', vus: ESTUDIANTES, duration: __ENV.DURACION || '120s', exec: 'estudiante' },
    panel: { executor: 'constant-vus', vus: 1, duration: __ENV.DURACION || '120s', exec: 'docente' },
  },
  thresholds: {
    'http_req_duration{pantalla:aula}': ['p(95)<500'],
    'http_req_duration{pantalla:entrar}': ['p(95)<500'],
    'http_req_duration{pantalla:panel}': ['p(95)<500'],
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
  },
};

let dentro = false;

export function estudiante() {
  if (!dentro) {
    sleep((__VU % 140) * 0.25);   // llegan a clase en unos 35 s, no todos en el mismo instante
    const grupo = Math.floor((__VU - 1) / POR_GRUPO) % 4 + 1;
    const n = String((__VU - 1) % POR_GRUPO + 1).padStart(2, '0');
    entrar(`estudiante k6 ${grupo}-${n}`, { tags: { pantalla: 'entrar' } });
    dentro = true;
  }
  const aula = { tags: { pantalla: 'aula' } };
  const reto = http.get(`${BASE}/reto`, aula);
  check(reto, { 'Reto de hoy: el reto del grupo o el aviso de que terminó': (r) => r.status === 200 && r.body.includes('Reto de hoy') });
  const id = RETO.exec(reto.body || '');
  const clave = CLAVE.exec(reto.body || '');
  if (id !== null && clave !== null) {
    sleep(3);   // lee el reto y elige
    const opcion = OPCION.exec(reto.body);
    const datos = { _clave: clave[1], tema: 'falacias', grupo: 'aula', respuesta: opcion ? opcion[1] : 'Lo reescribo sin generalizar: un caso no alcanza.' };
    const csrf = /<meta name="_csrf" content="([^"]+)"/.exec(reto.body);
    const respuesta = http.post(`${BASE}/dojo/retos/${id[1]}`, datos,
      Object.assign({ headers: { 'X-CSRF-TOKEN': csrf ? csrf[1] : '', 'HX-Request': 'true' } }, aula));
    check(respuesta, { 'Reto de hoy: respuesta calificada': (r) => r.status === 200 && r.body.includes('data-resultado=') });
  }
  const dojo = http.get(`${BASE}/dojo`, aula);
  check(dojo, { 'Dojo del estudiante': (r) => r.status === 200 });
  const trabajos = http.get(`${BASE}/mis-trabajos`, aula);
  check(trabajos, { 'Mis trabajos': (r) => r.status === 200 && r.body.includes('Mis trabajos') });
  sleep(4);
}

let grupos = null;

export async function docente() {
  if (grupos === null) {
    const csrf = await confirmarLlave(entrar('administrador k6'));
    if (!csrf) {
      fail('el administrador de carga no confirmó su llave');
    }
    const lista = http.get(`${BASE}/grupos`).body || '';
    grupos = [...lista.matchAll(GRUPO)].map((m) => m[1]).filter((v, i, a) => a.indexOf(v) === i);
  }
  const panel = { tags: { pantalla: 'panel' } };
  for (const g of grupos) {
    const r = http.get(`${BASE}/grupos/${g}/panel`, Object.assign({ headers: { 'HX-Request': 'true' } }, panel));
    check(r, { 'Panel del grupo: solo agregados': (x) => x.status === 200 && x.body.includes('id="panel"') });
  }
  sleep(10);
}
