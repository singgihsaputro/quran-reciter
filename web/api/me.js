// GET: who is signed in, their best stars and where they left off.
import { currentUser, db, json, ready } from './_lib.js'

export async function GET(request) {
  const id = await currentUser(request)
  if (!id) return json({ user: null })
  await ready()
  const [user, stars, state] = await db.batch([
    { sql: 'SELECT email, name, picture FROM users WHERE id = ?', args: [id] },
    { sql: 'SELECT verse, stars FROM stars WHERE user_id = ?', args: [id] },
    { sql: 'SELECT data FROM state WHERE user_id = ?', args: [id] },
  ], 'read')
  if (!user.rows.length) return json({ user: null })
  return json({
    user: user.rows[0],
    stars: Object.fromEntries(stars.rows.map(r => [r.verse, Number(r.stars)])),
    state: state.rows.length ? JSON.parse(state.rows[0].data) : null,
  })
}
