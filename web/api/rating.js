// POST { stars: 1..5, text?: string } — a rating, from a signed-in user or not.
import { body, currentUser, db, json, ready } from './_lib.js'

export async function POST(request) {
  const { stars, text } = await body(request)
  if (!Number.isInteger(stars) || stars < 1 || stars > 5) return json({ error: 'stars must be 1 to 5' }, { status: 400 })
  const note = typeof text === 'string' ? text.trim().slice(0, 1000) : ''
  await ready()
  // ponytail: no rate limit on anonymous ratings; add one (per IP, via Vercel's WAF) if spam shows up.
  await db.execute({
    sql: 'INSERT INTO ratings (user_id, stars, text, created_at) VALUES (?, ?, ?, ?)',
    args: [await currentUser(request), stars, note || null, Date.now()],
  })
  return json({ ok: true })
}
