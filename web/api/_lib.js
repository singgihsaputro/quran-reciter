// Shared by the API routes. The leading underscore keeps Vercel from serving
// this file as a route of its own.

import { createHmac, randomInt, timingSafeEqual } from 'node:crypto'
import { createClient } from '@libsql/client'
import { createRemoteJWKSet, jwtVerify, SignJWT } from 'jose'

// Turso in production; `file:local.db` (or `:memory:` in tests) locally.
export const db = createClient({
  url: process.env.TURSO_DATABASE_URL,
  authToken: process.env.TURSO_AUTH_TOKEN,
})

let schema
/** Creates the tables once per cold start; every statement is idempotent. */
export function ready() {
  schema ??= db.batch([
    `CREATE TABLE IF NOT EXISTS users (
      id TEXT PRIMARY KEY, email TEXT NOT NULL, name TEXT, picture TEXT,
      created_at INTEGER NOT NULL, last_seen INTEGER NOT NULL)`,
    `CREATE TABLE IF NOT EXISTS stars (
      user_id TEXT NOT NULL, verse TEXT NOT NULL, stars INTEGER NOT NULL, updated_at INTEGER NOT NULL,
      PRIMARY KEY (user_id, verse))`,
    `CREATE TABLE IF NOT EXISTS state (user_id TEXT PRIMARY KEY, data TEXT NOT NULL, updated_at INTEGER NOT NULL)`,
    `CREATE TABLE IF NOT EXISTS ratings (
      id INTEGER PRIMARY KEY AUTOINCREMENT, user_id TEXT, stars INTEGER NOT NULL CHECK (stars BETWEEN 1 AND 5),
      text TEXT, created_at INTEGER NOT NULL)`,
    // Sign-ins and taps on Donate / Share, for the owner's dashboard.
    `CREATE TABLE IF NOT EXISTS events (
      id INTEGER PRIMARY KEY AUTOINCREMENT, user_id TEXT, type TEXT NOT NULL, created_at INTEGER NOT NULL)`,
    'CREATE INDEX IF NOT EXISTS events_type_time ON events (type, created_at)',
    // One account per email, whether it signs in with Google or a code.
    'CREATE INDEX IF NOT EXISTS users_email ON users (lower(email))',
    // Email sign-in codes: only a keyed hash is stored, one pending code per email.
    `CREATE TABLE IF NOT EXISTS email_codes (
      email TEXT PRIMARY KEY, code_hash TEXT NOT NULL, expires_at INTEGER NOT NULL, attempts INTEGER NOT NULL,
      sent_at INTEGER NOT NULL, window_start INTEGER NOT NULL, window_count INTEGER NOT NULL)`,
  ], 'write')
  return schema
}

// ── Session: a signed cookie holding only the Google account id ─────────────

const COOKIE = 'rq_session'
const THIRTY_DAYS = 30 * 24 * 3600

function secret() {
  if (!process.env.SESSION_SECRET) throw new Error('SESSION_SECRET is not set')
  return new TextEncoder().encode(process.env.SESSION_SECRET)
}

export async function sessionCookie(userId) {
  const token = await new SignJWT({})
    .setProtectedHeader({ alg: 'HS256' })
    .setSubject(userId)
    .setIssuedAt()
    .setExpirationTime('30d')
    .sign(secret())
  return `${COOKIE}=${token}; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=${THIRTY_DAYS}`
}

export const clearedCookie = `${COOKIE}=; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=0`

/** The signed-in user's id, or null. */
export async function currentUser(request) {
  const token = request.headers.get('cookie')?.match(/(?:^|;\s*)rq_session=([^;]+)/)?.[1]
  if (!token) return null
  try {
    return (await jwtVerify(token, secret(), { algorithms: ['HS256'] })).payload.sub ?? null
  } catch {
    return null
  }
}

// ── Google sign-in: verify the ID token Google Identity Services handed the page ─

export const google = { keys: createRemoteJWKSet(new URL('https://www.googleapis.com/oauth2/v3/certs')) }

export async function verifyGoogle(credential) {
  const { payload } = await jwtVerify(credential, google.keys, {
    issuer: ['https://accounts.google.com', 'accounts.google.com'],
    audience: process.env.GOOGLE_CLIENT_ID,
  })
  if (!payload.email_verified) throw new Error('email not verified')
  return payload
}

/**
 * Signs a user in by email: the existing account with that email (from Google
 * or an earlier code) if there is one, otherwise a new one. Records the sign-in.
 */
export async function signInByEmail(email, { googleId = null, name = null, picture = null } = {}) {
  const now = Date.now()
  const { rows } = await db.execute({ sql: 'SELECT id FROM users WHERE lower(email) = lower(?) LIMIT 1', args: [email] })
  const id = rows[0]?.id ?? googleId ?? `email:${email.toLowerCase()}`
  await db.batch([
    {
      sql: `INSERT INTO users (id, email, name, picture, created_at, last_seen) VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET name = COALESCE(excluded.name, name),
              picture = COALESCE(excluded.picture, picture), last_seen = excluded.last_seen`,
      args: [id, email, name, picture, now, now],
    },
    { sql: "INSERT INTO events (user_id, type, created_at) VALUES (?, 'login', ?)", args: [id, now] },
  ], 'write')
  const user = (await db.execute({ sql: 'SELECT email, name, picture FROM users WHERE id = ?', args: [id] })).rows[0]
  return { id, user: { email: user.email, name: user.name ?? null, picture: user.picture ?? null } }
}

// ── Email codes ─────────────────────────────────────────────────────────────

export const newCode = () => String(randomInt(0, 1_000_000)).padStart(6, '0')

/** A keyed hash, so a leaked table reveals no codes. */
export const hashCode = (email, code) =>
  createHmac('sha256', secret()).update(`${email.toLowerCase()}:${code}`).digest('hex')

export function sameHash(a, b) {
  const x = Buffer.from(a, 'hex')
  const y = Buffer.from(b, 'hex')
  return x.length === y.length && timingSafeEqual(x, y)
}

/**
 * Sends the code. Brevo in production (BREVO_API_KEY, EMAIL_FROM); without a
 * key outside production, the code is printed to the server log for testing.
 * Tests replace `mailer.send`.
 */
export const mailer = {
  async send(to, code) {
    const key = process.env.BREVO_API_KEY
    if (!key || !process.env.EMAIL_FROM) {
      if (process.env.VERCEL_ENV === 'production') throw new Error('email is not configured')
      console.log(`[dev] sign-in code for ${to}: ${code}`)
      return
    }
    const res = await fetch('https://api.brevo.com/v3/smtp/email', {
      method: 'POST',
      headers: { 'api-key': key, 'content-type': 'application/json', accept: 'application/json' },
      body: JSON.stringify({
        sender: { email: process.env.EMAIL_FROM, name: process.env.EMAIL_FROM_NAME || 'Ayok Ngaji' },
        to: [{ email: to }],
        subject: `Kode masuk Ayok Ngaji: ${code}`,
        textContent: `Kode masuk Ayok Ngaji kamu: ${code}\nBerlaku 10 menit. Jangan berikan kode ini kepada siapa pun.\n\nYour Ayok Ngaji sign-in code: ${code}\nIt works for 10 minutes. Don't share it with anyone.`,
        htmlContent: `<div style="font-family:system-ui,sans-serif;max-width:420px;margin:auto;color:#2B2250">
          <h2 style="color:#6A4CFF">Ayok Ngaji 🌙</h2>
          <p>Kode masuk kamu / Your sign-in code:</p>
          <p style="font-size:32px;font-weight:800;letter-spacing:8px;margin:12px 0">${code}</p>
          <p style="color:#6B6690">Berlaku 10 menit. Jangan berikan kode ini kepada siapa pun.<br>Works for 10 minutes. Don't share it with anyone.</p>
        </div>`,
      }),
    })
    if (!res.ok) throw new Error(`brevo ${res.status}`)
  },
}

/** Whether this user id belongs to an email in ADMIN_EMAILS (comma-separated). */
export async function isAdmin(userId) {
  if (!userId) return false
  const admins = (process.env.ADMIN_EMAILS ?? '').split(',').map(e => e.trim().toLowerCase()).filter(Boolean)
  if (!admins.length) return false
  const { rows } = await db.execute({ sql: 'SELECT email FROM users WHERE id = ?', args: [userId] })
  return rows.length > 0 && admins.includes(String(rows[0].email).toLowerCase())
}

export const json = (data, init) => Response.json(data, init)

export async function body(request) {
  try { return await request.json() } catch { return {} }
}
