// Email sign-in with a one-time code.
// POST { email }        → sends a 6-digit code (one a minute, five an hour).
// PUT  { email, code }  → signs in; the code works once, for 10 minutes, 5 tries.
import { body, db, hashCode, json, mailer, newCode, ready, sameHash, sessionCookie, signInByEmail } from './_lib.js'

const MINUTE = 60e3
const TEN_MINUTES = 10 * MINUTE
const HOUR = 60 * MINUTE
const MAX_TRIES = 5
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/

const clean = value => (typeof value === 'string' ? value.trim().toLowerCase() : '')

export async function POST(request) {
  const email = clean((await body(request)).email)
  if (!EMAIL.test(email) || email.length > 254) return json({ error: 'bad_email' }, { status: 400 })
  await ready()
  const now = Date.now()
  const { rows } = await db.execute({ sql: 'SELECT sent_at, window_start, window_count FROM email_codes WHERE email = ?', args: [email] })
  const prev = rows[0]
  if (prev && now - Number(prev.sent_at) < MINUTE) {
    return json({ error: 'wait', retryIn: Math.ceil((MINUTE - (now - Number(prev.sent_at))) / 1000) }, { status: 429 })
  }
  const freshWindow = !prev || now - Number(prev.window_start) > HOUR
  const windowCount = freshWindow ? 1 : Number(prev.window_count) + 1
  if (windowCount > 5) return json({ error: 'wait', retryIn: Math.ceil((Number(prev.window_start) + HOUR - now) / 1000) }, { status: 429 })

  const code = newCode()
  try {
    await mailer.send(email, code)
  } catch {
    return json({ error: 'email_unavailable' }, { status: 503 })
  }
  await db.execute({
    sql: `INSERT INTO email_codes (email, code_hash, expires_at, attempts, sent_at, window_start, window_count)
          VALUES (?, ?, ?, 0, ?, ?, ?)
          ON CONFLICT(email) DO UPDATE SET code_hash = excluded.code_hash, expires_at = excluded.expires_at,
            attempts = 0, sent_at = excluded.sent_at, window_start = excluded.window_start, window_count = excluded.window_count`,
    args: [email, hashCode(email, code), now + TEN_MINUTES, now, freshWindow ? now : Number(prev.window_start), windowCount],
  })
  return json({ ok: true })
}

export async function PUT(request) {
  const input = await body(request)
  const email = clean(input.email)
  const code = typeof input.code === 'string' ? input.code.replace(/\D/g, '') : ''
  if (!EMAIL.test(email) || code.length !== 6) return json({ error: 'wrong', left: MAX_TRIES }, { status: 400 })
  await ready()
  const { rows } = await db.execute({ sql: 'SELECT code_hash, expires_at, attempts FROM email_codes WHERE email = ?', args: [email] })
  const pending = rows[0]
  if (!pending || Date.now() > Number(pending.expires_at)) return json({ error: 'expired' }, { status: 410 })
  if (Number(pending.attempts) >= MAX_TRIES) return json({ error: 'too_many' }, { status: 429 })

  if (!sameHash(pending.code_hash, hashCode(email, code))) {
    await db.execute({ sql: 'UPDATE email_codes SET attempts = attempts + 1 WHERE email = ?', args: [email] })
    return json({ error: 'wrong', left: MAX_TRIES - Number(pending.attempts) - 1 }, { status: 400 })
  }
  // Used once: a correct code can't be replayed.
  await db.execute({ sql: 'UPDATE email_codes SET expires_at = 0 WHERE email = ?', args: [email] })
  const { id, user } = await signInByEmail(email)
  return json({ user }, { headers: { 'set-cookie': await sessionCookie(id) } })
}
