// POST { credential, handoff? }: sign in with the ID token from Google Identity Services.
// handoff: the app on an iPhone home screen can't finish Google sign-in itself
// (the popup opens in Safari and never reports back), so it opens Safari with a
// random code; signing in there parks the account under that code for
// /api/handoff to hand to the app.
// DELETE: sign out.
import { HANDOFF, HANDOFF_TTL, body, clearedCookie, db, json, ready, sessionCookie, signInByEmail, verifyGoogle } from './_lib.js'

export async function POST(request) {
  const { credential, handoff } = await body(request)
  if (typeof credential !== 'string') return json({ error: 'credential required' }, { status: 400 })
  let account
  try {
    account = await verifyGoogle(credential)
  } catch {
    return json({ error: 'invalid credential' }, { status: 401 })
  }
  await ready()
  const { id, user } = await signInByEmail(account.email, {
    googleId: account.sub, name: account.name ?? null, picture: account.picture ?? null,
  })
  if (typeof handoff === 'string' && HANDOFF.test(handoff)) {
    const now = Date.now()
    await db.batch([
      { sql: 'DELETE FROM handoffs WHERE created_at < ?', args: [now - HANDOFF_TTL] },
      { sql: 'INSERT OR IGNORE INTO handoffs (id, user_id, created_at) VALUES (?, ?, ?)', args: [handoff, id, now] },
    ], 'write')
  }
  return json({ user }, { headers: { 'set-cookie': await sessionCookie(id) } })
}

export function DELETE() {
  return json({ ok: true }, { headers: { 'set-cookie': clearedCookie } })
}
