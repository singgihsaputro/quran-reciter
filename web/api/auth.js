// POST: sign in with the ID token from Google Identity Services.
// DELETE: sign out.
import { body, clearedCookie, db, json, ready, sessionCookie, verifyGoogle } from './_lib.js'

export async function POST(request) {
  const { credential } = await body(request)
  if (typeof credential !== 'string') return json({ error: 'credential required' }, { status: 400 })
  let account
  try {
    account = await verifyGoogle(credential)
  } catch {
    return json({ error: 'invalid credential' }, { status: 401 })
  }
  await ready()
  const now = Date.now()
  await db.execute({
    sql: `INSERT INTO users (id, email, name, picture, created_at, last_seen) VALUES (?, ?, ?, ?, ?, ?)
          ON CONFLICT(id) DO UPDATE SET email = excluded.email, name = excluded.name,
            picture = excluded.picture, last_seen = excluded.last_seen`,
    args: [account.sub, account.email, account.name ?? null, account.picture ?? null, now, now],
  })
  return json(
    { user: { email: account.email, name: account.name ?? null, picture: account.picture ?? null } },
    { headers: { 'set-cookie': await sessionCookie(account.sub) } },
  )
}

export function DELETE() {
  return json({ ok: true }, { headers: { 'set-cookie': clearedCookie } })
}
