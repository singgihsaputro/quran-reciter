// POST: sign in with the ID token from Google Identity Services.
// DELETE: sign out.
import { body, clearedCookie, json, ready, sessionCookie, signInByEmail, verifyGoogle } from './_lib.js'

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
  // Same email as an account made with a code → the same account, same stars.
  const { id, user } = await signInByEmail(account.email, {
    googleId: account.sub, name: account.name ?? null, picture: account.picture ?? null,
  })
  return json({ user }, { headers: { 'set-cookie': await sessionCookie(id) } })
}

export function DELETE() {
  return json({ ok: true }, { headers: { 'set-cookie': clearedCookie } })
}
