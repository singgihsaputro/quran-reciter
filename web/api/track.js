// POST — usage for the owner's dashboard, sent with navigator.sendBeacon:
//   { type: 'view',   device, page: 'home'|'surah'|'story'|'support', surah? }  (surah = story number for 'story')
//   { type: 'recite', device, surah, verse?, mode: 'verse'|'surah', score: 0..100, stars: 0..3 }
//   { type: 'signin', device, attempt, step: 'tap'|'done'|'fail', browser, context: 'browser'|'in-app'|'pwa' }
import { body, currentUser, db, json, ready } from './_lib.js'

const PAGES = new Set(['home', 'surah', 'story', 'support'])
const STEPS = new Set(['tap', 'done', 'fail'])
const CONTEXTS = new Set(['browser', 'in-app', 'pwa'])
const int = (v, lo, hi) => (Number.isInteger(v) && v >= lo && v <= hi ? v : null)

export async function POST(request) {
  const b = await body(request)
  const device = typeof b.device === 'string' && /^[a-z0-9-]{8,40}$/i.test(b.device) ? b.device : null
  if (b.type === 'signin') {
    if (!STEPS.has(b.step) || !CONTEXTS.has(b.context) || typeof b.attempt !== 'string' || !/^[a-z0-9-]{8,40}$/i.test(b.attempt)
      || typeof b.browser !== 'string' || !/^[\w .·()-]{1,40}$/u.test(b.browser)) return json({ error: 'bad event' }, { status: 400 })
    await ready()
    await db.execute({
      sql: 'INSERT INTO signin (device, attempt, step, browser, context, user_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)',
      args: [device, b.attempt, b.step, b.browser, b.context, await currentUser(request), Date.now()],
    })
    return json({ ok: true })
  }
  let row
  if (b.type === 'view' && PAGES.has(b.page)) {
    row = { page: b.page, surah: int(b.surah, 0, 114), verse: null, mode: null, score: null, stars: null }
  } else if (b.type === 'recite' && int(b.surah, 1, 114) && (b.mode === 'verse' || b.mode === 'surah')
    && int(b.score, 0, 100) !== null && int(b.stars, 0, 3) !== null) {
    row = { page: 'surah', surah: b.surah, verse: b.mode === 'verse' ? int(b.verse, 1, 300) : null, mode: b.mode, score: b.score, stars: b.stars }
  } else {
    return json({ error: 'bad event' }, { status: 400 })
  }
  await ready()
  // ponytail: no rate limit on anonymous usage events; add a per-IP limit (Vercel WAF) if counts look inflated.
  await db.execute({
    sql: `INSERT INTO activity (user_id, device, type, page, surah, verse, mode, score, stars, created_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    args: [await currentUser(request), device, b.type, row.page, row.surah, row.verse, row.mode, row.score, row.stars, Date.now()],
  })
  return json({ ok: true })
}
