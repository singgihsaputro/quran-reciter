// PUT { stars?: { "surah:verse": 0..3 }, state?: { surah, verse, whole, lang } }
// Stars only ever go up: the best try on any device wins.
import { body, currentUser, db, json, ready } from './_lib.js'

const VERSE = /^\d{1,3}:\d{1,3}$/

export async function PUT(request) {
  const id = await currentUser(request)
  if (!id) return json({ error: 'sign in first' }, { status: 401 })
  const { stars = {}, state } = await body(request)
  const entries = Object.entries(stars).filter(([verse, n]) => VERSE.test(verse) && Number.isInteger(n) && n >= 1 && n <= 3)
  if (entries.length > 700) return json({ error: 'too many verses' }, { status: 413 })
  await ready()
  const now = Date.now()
  const writes = entries.map(([verse, n]) => ({
    sql: `INSERT INTO stars (user_id, verse, stars, updated_at) VALUES (?, ?, ?, ?)
          ON CONFLICT(user_id, verse) DO UPDATE SET stars = MAX(stars, excluded.stars), updated_at = excluded.updated_at`,
    args: [id, verse, n, now],
  }))
  if (state && typeof state === 'object') {
    const clean = {
      surah: Number.isInteger(state.surah) ? state.surah : null,
      verse: Number.isInteger(state.verse) ? state.verse : null,
      whole: state.whole === true,
      lang: state.lang === 'id' ? 'id' : 'en',
    }
    writes.push({
      sql: `INSERT INTO state (user_id, data, updated_at) VALUES (?, ?, ?)
            ON CONFLICT(user_id) DO UPDATE SET data = excluded.data, updated_at = excluded.updated_at`,
      args: [id, JSON.stringify(clean), now],
    })
  }
  if (writes.length) await db.batch(writes, 'write')
  return json({ ok: true })
}
