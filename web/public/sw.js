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
