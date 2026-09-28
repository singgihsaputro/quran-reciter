// GET: Vercel Cron, every day at 18:00 WIB (vercel.json). Sends a gentle nudge to
// every browser with the reminder on that hasn't opened Ayok Ngaji for about a
// day — once per absence, so a child who stays away isn't pinged every evening.
import webpush from 'web-push'
import { db, json, ready } from './_lib.js'

// Swapped out in tests.
export const push = { send: (sub, payload, options) => webpush.sendNotification(sub, payload, options) }

const MESSAGES = {
  id: [
    { title: 'Bintangmu menunggu! ⭐', body: 'Yuk baca satu ayat hari ini bareng Ayok Ngaji.' },
    { title: 'Ada cerita baru malam ini 🌙', body: 'Dengarkan kisah dari Al-Qur\'an sebelum tidur.' },
    { title: 'Assalamu\'alaikum! 👋', body: 'Sudah ngaji hari ini? Satu ayat saja sudah hebat.' },
  ],
  en: [
    { title: 'Your stars are waiting! ⭐', body: "Let's recite one verse today with Ayok Ngaji." },
    { title: 'A new story tonight 🌙', body: "Listen to a story from the Qur'an before bed." },
    { title: 'Assalamu\'alaikum! 👋', body: 'Recited today? Just one verse is great.' },
  ],
}

// "Didn't open it today": a day, less a few hours so the evening run still
// catches someone who opened it late yesterday.
const AWAY = 20 * 3600 * 1000

export async function GET(request) {
  if (!process.env.CRON_SECRET || request.headers.get('authorization') !== `Bearer ${process.env.CRON_SECRET}`) {
    return json({ error: 'unauthorized' }, { status: 401 })
  }
  await ready()
  const now = Date.now()
  const { rows } = await db.execute({
    sql: 'SELECT endpoint, sub, lang FROM push WHERE last_open < ? AND (last_sent IS NULL OR last_sent < last_open)',
    args: [now - AWAY],
  })
  const options = {
    TTL: 12 * 3600,
    vapidDetails: { subject: 'https://ayokngaji.vercel.app', publicKey: process.env.VAPID_PUBLIC_KEY, privateKey: process.env.VAPID_PRIVATE_KEY },
  }
  const day = Math.floor(now / 864e5)
  // ponytail: one parallel burst; batch it if the list outgrows the function's time limit.
  const results = await Promise.allSettled(rows.map(row => {
    const messages = MESSAGES[row.lang] ?? MESSAGES.id
    return push.send(JSON.parse(row.sub), JSON.stringify(messages[day % messages.length]), options)
  }))
  const writes = []
  let sent = 0, removed = 0
  results.forEach((result, i) => {
    if (result.status === 'fulfilled') {
      sent++
      writes.push({ sql: 'UPDATE push SET last_sent = ? WHERE endpoint = ?', args: [now, rows[i].endpoint] })
    } else if ([404, 410].includes(result.reason?.statusCode)) { // unsubscribed or app removed
      removed++
      writes.push({ sql: 'DELETE FROM push WHERE endpoint = ?', args: [rows[i].endpoint] })
    }
  })
  if (writes.length) await db.batch(writes, 'write')
  return json({ sent, removed, failed: rows.length - sent - removed })
}
