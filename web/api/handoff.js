// POST { id }: the iPhone home-screen app picks up a sign-in finished in Safari
// (see auth.js). Answers { user: null } until then; each code works once.
import { HANDOFF, HANDOFF_TTL, body, db, json, ready, sessionCookie } from './_lib.js'
import { GET as me } from './me.js'

export async function POST(request) {
  const { id } = await body(request)
  if (typeof id !== 'string' || !HANDOFF.test(id)) return json({ error: 'bad code' }, { status: 400 })
  await ready()
  const { rows } = await db.execute({
    sql: 'DELETE FROM handoffs WHERE id = ? AND created_at > ? RETURNING user_id',
    args: [id, Date.now() - HANDOFF_TTL],
  })
  if (!rows.length) return json({ user: null })
  const cookie = await sessionCookie(rows[0].user_id)
  const account = await me(new Request(request.url, { headers: { cookie: cookie.split(';')[0] } }))
  return json(await account.json(), { headers: { 'set-cookie': cookie } })
}
