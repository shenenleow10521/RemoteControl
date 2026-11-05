const CACHE = 'rec-remote-v1';
const ASSETS = ['./','./index.html','./style.css','./app.mjs','./controller.mjs','./transports.mjs','./manifest.webmanifest','./icon.svg','./icon-192.png','./icon-512.png','./REC_BLE_Robot.ino'];
self.addEventListener('install', event => event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(ASSETS))));
self.addEventListener('activate', event => event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(key => key !== CACHE).map(key => caches.delete(key))))));
self.addEventListener('fetch', event => {
  if (event.request.method !== 'GET' || new URL(event.request.url).origin !== self.location.origin) return;
  event.respondWith(fetch(event.request).catch(() => caches.match(event.request)));
});
