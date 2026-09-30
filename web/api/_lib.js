// Shared by the API routes. The leading underscore keeps Vercel from serving
// this file as a route of its own.

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
    'CREATE INDEX IF NOT EXISTS users_email ON users (lower(email))',
    // What people open and how their recitations score. device is a random id
    // kept in the browser, so guests count as visitors without being identified.
    `CREATE TABLE IF NOT EXISTS activity (
      id INTEGER PRIMARY KEY AUTOINCREMENT, user_id TEXT, device TEXT, type TEXT NOT NULL,
      page TEXT, surah INTEGER, verse INTEGER, mode TEXT, score INTEGER, stars INTEGER, created_at INTEGER NOT NULL)`,
    'CREATE INDEX IF NOT EXISTS activity_type_time ON activity (type, created_at)',
    // One-time codes for signing the iPhone home-screen app in via Safari (see auth.js).
    'CREATE TABLE IF NOT EXISTS handoffs (id TEXT PRIMARY KEY, user_id TEXT NOT NULL, created_at INTEGER NOT NULL)',
    // Browsers with the daily reminder on (api/push.js), and when each last opened the app.
    `CREATE TABLE IF NOT EXISTS push (
      endpoint TEXT PRIMARY KEY, sub TEXT NOT NULL, lang TEXT NOT NULL, last_open INTEGER NOT NULL, last_sent INTEGER)`,
    // Each tap on "Sign in with Google", and whether that attempt finished — by
    // browser and by where it ran (a browser, an app's built-in browser, or the
    // home-screen app). One attempt id per tap ties its steps together.
    `CREATE TABLE IF NOT EXISTS signin (
      id INTEGER PRIMARY KEY AUTOINCREMENT, device TEXT, attempt TEXT NOT NULL, step TEXT NOT NULL,
      browser TEXT, context TEXT, user_id TEXT, created_at INTEGER NOT NULL)`,
    'CREATE INDEX IF NOT EXISTS signin_attempt ON signin (attempt)',
  ], 'write')
  return schema
}

/** A handoff code: a random UUID made by the app. They live ten minutes. */
export const HANDOFF = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
export const HANDOFF_TTL = 10 * 60 * 1000

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
 * Signs a Google account in: the existing account with that email if there is
 * one, otherwise a new one keyed by the Google id. Records the sign-in.
 */
export async function signInByEmail(email, { googleId, name = null, picture = null }) {
  const now = Date.now()
  const { rows } = await db.execute({ sql: 'SELECT id FROM users WHERE lower(email) = lower(?) LIMIT 1', args: [email] })
  const id = rows[0]?.id ?? googleId
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
