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

export const json = (data, init) => Response.json(data, init)

export async function body(request) {
  try { return await request.json() } catch { return {} }
}
