// POST: sign in with the ID token from Google Identity Services.
//   - JSON { credential } from the popup flow (browsers, Android);
//   - a form post from Google itself in redirect mode — used by the app when
//     installed on an iPhone home screen, where the popup opens in Safari and
//     can never report back (the analytics page too, via the rq_next cookie). That form carries a CSRF token that must match
//     Google's g_csrf_token cookie; on success we send the child back to the app.
// DELETE: sign out.
import { body, clearedCookie, json, ready, sessionCookie, signInByEmail, verifyGoogle } from './_lib.js'

async function signIn(credential) {
  const account = await verifyGoogle(credential)
  await ready()
  return signInByEmail(account.email, {
    googleId: account.sub, name: account.name ?? null, picture: account.picture ?? null,
  })
}

export async function POST(request) {
  if ((request.headers.get('content-type') ?? '').includes('application/x-www-form-urlencoded')) {
    const form = new URLSearchParams(await request.text())
    const cookie = request.headers.get('cookie')?.match(/(?:^|;\s*)g_csrf_token=([^;]+)/)?.[1]
    // The analytics page sets rq_next so the owner lands back on the dashboard.
    const toAnalytics = /(?:^|;\s*)rq_next=analytics(?:;|$)/.test(request.headers.get('cookie') ?? '')
    const back = (hash, extra = {}) => new Response(null, {
      status: 303,
      headers: [
        ['location', toAnalytics ? '/analytics' : `/#/${hash}`],
        ...(toAnalytics ? [['set-cookie', 'rq_next=; Path=/; Max-Age=0; SameSite=None; Secure']] : []),
        ...Object.entries(extra),
      ],
    })
    if (!cookie || cookie !== form.get('g_csrf_token')) return back('support')
    try {
      const { id } = await signIn(form.get('credential') ?? '')
      return back('', { 'set-cookie': await sessionCookie(id) })
    } catch {
      return back('support')
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
