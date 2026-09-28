// GET: the owner's dashboard — sign-ins, reviews, donate taps. Admins only
// (ADMIN_EMAILS); everyone else gets 401 or 403 and no data.
import { currentUser, db, isAdmin, json, ready } from './_lib.js'

const DAY = 864e5

export async function GET(request) {
  const id = await currentUser(request)
  if (!id) return json({ error: 'sign in first' }, { status: 401 })
  await ready()
  if (!(await isAdmin(id))) return json({ error: 'not an admin' }, { status: 403 })

  const now = Date.now()
  const week = now - 7 * DAY
  const [totals, logins, ratings, taps, daily, usage, recitations, surahs, pages] = await db.batch([
    {
      sql: `SELECT
        (SELECT COUNT(*) FROM users) AS users,
        (SELECT COUNT(*) FROM users WHERE created_at >= ?) AS new_users_7d,
        (SELECT COUNT(DISTINCT user_id) FROM events WHERE type = 'login' AND created_at >= ?) AS active_7d,
        (SELECT COUNT(*) FROM ratings) AS ratings,
        (SELECT ROUND(AVG(stars), 2) FROM ratings) AS rating_avg,
        (SELECT COUNT(*) FROM events WHERE type = 'donate_tap') AS donate_taps,
        (SELECT COUNT(*) FROM events WHERE type = 'share') AS shares`,
      args: [now - 7 * DAY, now - 7 * DAY],
    },
    `SELECT u.email, u.name, e.created_at FROM events e JOIN users u ON u.id = e.user_id
      WHERE e.type = 'login' ORDER BY e.created_at DESC LIMIT 200`,
    `SELECT r.stars, r.text, r.created_at, u.email FROM ratings r LEFT JOIN users u ON u.id = r.user_id
      ORDER BY r.created_at DESC LIMIT 200`,
    `SELECT e.type, e.created_at, u.email FROM events e LEFT JOIN users u ON u.id = e.user_id
      WHERE e.type IN ('donate_tap', 'share') ORDER BY e.created_at DESC LIMIT 200`,
    {
      sql: `SELECT date(created_at / 1000, 'unixepoch', '+7 hours') AS day, type, COUNT(*) AS n
            FROM events WHERE created_at >= ? GROUP BY day, type ORDER BY day`,
      args: [now - 30 * DAY],
    },
    {
      sql: `SELECT
        (SELECT COUNT(*) FROM activity WHERE type = 'view' AND created_at >= ?) AS views_7d,
        (SELECT COUNT(DISTINCT COALESCE(user_id, device)) FROM activity WHERE created_at >= ?) AS visitors_7d,
        (SELECT COUNT(*) FROM activity WHERE type = 'recite' AND created_at >= ?) AS recitations_7d,
        (SELECT COUNT(*) FROM activity WHERE type = 'recite' AND mode = 'surah' AND created_at >= ?) AS whole_surah_7d,
        (SELECT ROUND(AVG(score)) FROM activity WHERE type = 'recite' AND created_at >= ?) AS avg_score_7d`,
      args: [week, week, week, week, week],
    },
    `SELECT a.surah, a.verse, a.mode, a.score, a.stars, a.created_at, u.email
      FROM activity a LEFT JOIN users u ON u.id = a.user_id
      WHERE a.type = 'recite' ORDER BY a.created_at DESC LIMIT 300`,
    `SELECT surah,
        SUM(type = 'view') AS views,
        COUNT(DISTINCT CASE WHEN type = 'view' THEN COALESCE(user_id, device) END) AS viewers,
        SUM(type = 'recite') AS recitations,
        SUM(type = 'recite' AND mode = 'surah') AS whole_surah,
        ROUND(AVG(CASE WHEN type = 'recite' THEN score END)) AS avg_score,
        ROUND(AVG(CASE WHEN type = 'recite' THEN stars END), 1) AS avg_stars
      FROM activity WHERE page = 'surah' AND surah IS NOT NULL
      GROUP BY surah ORDER BY views DESC, recitations DESC`,
    `SELECT page, COUNT(*) AS views, COUNT(DISTINCT COALESCE(user_id, device)) AS viewers,
        COUNT(DISTINCT user_id) AS signed_in
      FROM activity WHERE type = 'view' GROUP BY page ORDER BY views DESC`,
  ], 'read')

  const plain = rs => rs.rows.map(r => Object.fromEntries(rs.columns.map(c => [c, r[c] == null ? null : typeof r[c] === 'bigint' ? Number(r[c]) : r[c]])))
  return json({
    totals: { ...plain(totals)[0], ...plain(usage)[0] },
    logins: plain(logins), ratings: plain(ratings), taps: plain(taps), daily: plain(daily),
    recitations: plain(recitations), surahs: plain(surahs), pages: plain(pages),
  })
}
