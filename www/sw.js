// Service worker de la versión web (GitHub Pages). La APK no lo usa.
// Cambia VERSION cada vez que publiques cambios para que los usuarios reciban el aviso de actualización.
const VERSION = 'mebuc-v2';
const ARCHIVOS = ['./', './index.html', './manifest.json', './icon-192.png', './icon-512.png'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(VERSION).then(c => c.addAll(ARCHIVOS)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil((async () => {
    const claves = await caches.keys();
    const habiaVersionAnterior = claves.some(k => k !== VERSION);
    await Promise.all(claves.filter(k => k !== VERSION).map(k => caches.delete(k)));
    await self.clients.claim();
    if (habiaVersionAnterior) {
      const clientes = await self.clients.matchAll({ type: 'window' });
      clientes.forEach(c => c.postMessage({ type: 'NEW_VERSION' }));
    }
  })());
});

// Red primero para la página (siempre la última versión si hay internet), caché si no hay conexión
self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET' || new URL(e.request.url).origin !== location.origin) return;
  e.respondWith(
    fetch(e.request)
      .then(r => { const copia = r.clone(); caches.open(VERSION).then(c => c.put(e.request, copia)); return r; })
      .catch(() => caches.match(e.request).then(r => r || caches.match('./index.html')))
  );
});
