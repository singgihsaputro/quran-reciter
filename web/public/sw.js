// Keeps Ayok Ngaji opening on a poor connection: the app's own files come from
// the network when it answers and from the last copy when it doesn't. The API
// and the audio servers are never cached here.
const CACHE = 'ayok-ngaji'

self.addEventListener('install', () => self.skipWaiting())
self.addEventListener('activate', event => event.waitUntil(self.clients.claim()))

self.addEventListener('fetch', event => {
  const url = new URL(event.request.url)
  if (event.request.method !== 'GET' || url.origin !== location.origin || url.pathname.startsWith('/api/')) return
  event.respondWith(
    fetch(event.request)
      .then(response => {
        if (response.ok) {
          const copy = response.clone()
          caches.open(CACHE).then(cache => cache.put(event.request, copy))
        }
        return response
      })
      .catch(() => caches.match(event.request).then(hit => hit ?? Response.error())),
  )
})
