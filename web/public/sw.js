// Keeps Ayok Ngaji opening on a poor connection: the app's own files come from
// the network when it answers and from the last copy when it doesn't. The API
// and the audio servers are never cached here.
const CACHE = 'ayok-ngaji'

// Saved up front, so the app — and the support QRIS — still open if the server
// is ever down, e.g. paused for going over the free hosting quota.
const CORE = ['/', '/app.js', '/matcher.js', '/media.js', '/strings.js', '/sfx.js', '/style.css', '/quran.json',
  '/stories.json', '/story_verses.json', '/manifest.webmanifest', '/icon-192.png', '/apple-touch-icon.png',
  '/qris-ayok-ngaji.jpg', '/500']

self.addEventListener('install', event => {
  self.skipWaiting()
  // One by one, so a single missing file doesn't cancel the rest.
  event.waitUntil(caches.open(CACHE).then(cache => Promise.all(CORE.map(path => cache.add(path).catch(() => {})))))
})
self.addEventListener('activate', event => event.waitUntil(self.clients.claim()))

// The last good copy of a request; for a page, the app itself, else the
// "server is resting" page.
async function saved(request) {
  const hit = await caches.match(request, { ignoreSearch: true })
  if (hit) return hit
  if (request.mode === 'navigate') return (await caches.match('/')) ?? (await caches.match('/500')) ?? Response.error()
  return Response.error()
}

self.addEventListener('fetch', event => {
  const url = new URL(event.request.url)
  if (event.request.method !== 'GET' || url.origin !== location.origin || url.pathname.startsWith('/api/')) return
  event.respondWith(
    fetch(event.request)
      .then(response => {
        if (response.ok) {
          const copy = response.clone()
          caches.open(CACHE).then(cache => cache.put(event.request, copy))
          return response
        }
        // A server error (a paused deployment answers 503) gets the saved copy; a 404 stays a 404.
        return response.status >= 500 ? saved(event.request).then(r => (r.type === 'error' ? response : r)) : response
      })
      .catch(() => saved(event.request)),
  )
})

// The daily reminder (api/remind.js). iPhones drop the subscription unless
// every push shows a notification, so this always shows one.
self.addEventListener('push', event => {
  let message = {}
  try { message = event.data?.json() ?? {} } catch { /* not JSON: use the default */ }
  event.waitUntil(self.registration.showNotification(message.title ?? 'Ayok Ngaji', {
    body: message.body ?? '',
    icon: '/icon-192.png',
    badge: '/icon-192.png',
    tag: 'reminder', // a new one replaces the last instead of piling up
  }))
})

self.addEventListener('notificationclick', event => {
  event.notification.close()
  event.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    .then(open => open[0]?.focus() ?? self.clients.openWindow('/')))
})
