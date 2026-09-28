// POST { subscription, lang }: turns the daily reminder on for this browser. The
// app also sends it on every open, so api/remind.js only nudges browsers that
// haven't opened Ayok Ngaji for a day.
// DELETE { endpoint }: turns it off.
import { body, db, json, ready } from './_lib.js'

// Only real push services: the reminder job posts to these addresses.
const PUSH_HOSTS = /(^|\.)(fcm\.googleapis\.com|push\.services\.mozilla\.com|push\.apple\.com|notify\.windows\.com)$/

function valid(sub) {
  try {
    const url = new URL(sub.endpoint)
    return url.protocol === 'https:' && PUSH_HOSTS.test(url.hostname) && sub.endpoint.length < 1000
      && typeof sub.keys?.p256dh === 'string' && sub.keys.p256dh.length < 200
      && typeof sub.keys?.auth === 'string' && sub.keys.auth.length < 100
  } catch {
    return false
  }
}

export async function POST(request) {
  const { subscription: sub, lang } = await body(request)
  if (!sub || !valid(sub)) return json({ error: 'bad subscription' }, { status: 400 })
  await ready()
  await db.execute({
    sql: `INSERT INTO push (endpoint, sub, lang, last_open) VALUES (?, ?, ?, ?)
          ON CONFLICT(endpoint) DO UPDATE SET sub = excluded.sub, lang = excluded.lang, last_open = excluded.last_open`,
    args: [
      sub.endpoint,
      JSON.stringify({ endpoint: sub.endpoint, keys: { p256dh: sub.keys.p256dh, auth: sub.keys.auth } }),
      lang === 'en' ? 'en' : 'id',
      Date.now(),
    ],
  })
  return json({ ok: true })
}

export async function DELETE(request) {
  const { endpoint } = await body(request)
  if (typeof endpoint !== 'string') return json({ error: 'endpoint required' }, { status: 400 })
  await ready()
  await db.execute({ sql: 'DELETE FROM push WHERE endpoint = ?', args: [endpoint] })
  return json({ ok: true })
}
