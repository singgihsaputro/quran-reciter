// POST { type: 'donate_tap' | 'share' } — a tap worth counting, signed in or not.
import { body, currentUser, db, json, ready } from './_lib.js'

const TYPES = new Set(['donate_tap', 'share'])

export async function POST(request) {
  const { type } = await body(request)
  if (!TYPES.has(type)) return json({ error: 'unknown event' }, { status: 400 })
  await ready()
  // ponytail: anonymous taps aren't rate limited; add a per-IP limit (Vercel WAF) if counts look inflated.
  await db.execute({
    sql: 'INSERT INTO events (user_id, type, created_at) VALUES (?, ?, ?)',
    args: [await currentUser(request), type, Date.now()],
  })
  return json({ ok: true })
}
