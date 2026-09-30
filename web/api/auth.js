// POST: sign in with a Google ID token.
//   - JSON { credential } from Google's popup button (browsers, Android);
//   - a form post from Google itself, the end of the full-page sign-in the
//     iPhone home-screen app opens in Safari (Google's popup can't report back
//     to that app). Its `state` and `nonce` are a random code the app made; the
//     account is parked under that code for /api/handoff to hand to the app.
// DELETE: sign out.
import { HANDOFF, HANDOFF_TTL, body, clearedCookie, db, json, ready, sessionCookie, signInByEmail, verifyGoogle } from './_lib.js'

async function signIn(idToken) {
  const account = await verifyGoogle(idToken)
  await ready()
  return { account, ...(await signInByEmail(account.email, {
    googleId: account.sub, name: account.name ?? null, picture: account.picture ?? null,
  })) }
}

export async function POST(request) {
  if ((request.headers.get('content-type') ?? '').includes('application/x-www-form-urlencoded')) {
    const form = new URLSearchParams(await request.text())
    const code = form.get('state') ?? ''
    // The code rides along so the page can count a failed attempt against its tap.
    const back = result => new Response(null, { status: 303, headers: { location: `/#/signin/${result}${HANDOFF.test(code) ? `/${code}` : ''}` } })
    try {
      if (!HANDOFF.test(code)) throw new Error('bad state')
      const { account, id } = await signIn(form.get('id_token') ?? '')
      if (account.nonce !== code) throw new Error('nonce mismatch')
      const now = Date.now()
      await db.batch([
        { sql: 'DELETE FROM handoffs WHERE created_at < ?', args: [now - HANDOFF_TTL] },
        { sql: 'INSERT OR IGNORE INTO handoffs (id, user_id, created_at) VALUES (?, ?, ?)', args: [code, id, now] },
      ], 'write')
      return back('done')
    } catch {
      return back('failed') // includes the child cancelling at Google (error=access_denied)
    }
  }

  const { credential } = await body(request)
  if (typeof credential !== 'string') return json({ error: 'credential required' }, { status: 400 })
  try {
    const { id, user } = await signIn(credential)
    return json({ user }, { headers: { 'set-cookie': await sessionCookie(id) } })
  } catch {
    return json({ error: 'invalid credential' }, { status: 401 })
  }
}

export function DELETE() {
  return json({ ok: true }, { headers: { 'set-cookie': clearedCookie } })
}
