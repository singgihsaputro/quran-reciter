// The owner's dashboard: who signed in and when, reviews, and taps on Donate
// and Share. The page itself is public; /api/analytics only answers admins.

const $dash = document.getElementById('dash')
const when = ms => new Date(ms).toLocaleString('id-ID', { dateStyle: 'medium', timeStyle: 'short' })

function h(tag, props = {}, ...children) {
  const el = document.createElement(tag)
  for (const [k, v] of Object.entries(props)) {
    if (v == null || v === false) continue
    if (k.startsWith('on')) el.addEventListener(k.slice(2), v)
    else if (k === 'class') el.className = v
    else el.setAttribute(k, v)
  }
  for (const c of children.flat(Infinity)) if (c != null && c !== false) el.append(c)
  return el
}

const who = email => email ?? h('span', { class: 'guest' }, 'guest')

function table(headers, rows) {
  if (!rows.length) return h('p', { class: 'empty' }, 'Nothing yet.')
  return h('div', { class: 'scroll' }, h('table', {},
    h('thead', {}, h('tr', {}, headers.map(([label, num]) => h('th', { class: num ? 'num' : null }, label)))),
    h('tbody', {}, rows)))
}

/** Sign-ins per day for the last 30 days, Jakarta time. */
function chart(daily) {
  const days = []
  for (let i = 29; i >= 0; i--) days.push(new Date(Date.now() + 7 * 3600e3 - i * 864e5).toISOString().slice(0, 10))
  const count = day => daily.filter(d => d.day === day && d.type === 'login').reduce((n, d) => n + d.n, 0)
  const counts = days.map(count)
  const max = Math.max(1, ...counts)
  return [
    h('div', { class: 'bars', role: 'img', 'aria-label': `Sign-ins per day, last 30 days. Total ${counts.reduce((a, b) => a + b, 0)}.` },
      days.map((day, i) => h('div', { style: `height:${Math.max(2, (counts[i] / max) * 100)}%`, 'data-tip': `${day}: ${counts[i]}` }))),
    h('div', { class: 'axis' }, h('span', {}, days[0]), h('span', {}, 'today')),
  ]
}

async function signInGate(message) {
  const { googleClientId } = await fetch('/api/config').then(r => r.json()).catch(() => ({}))
  const slot = h('div')
  $dash.replaceChildren(h('section', { class: 'card gate' }, h('h2', {}, 'Ayok Ngaji · Analytics'), h('p', {}, message), slot))
  if (!googleClientId) return slot.append(h('p', { class: 'empty' }, 'Sign-in is not set up (GOOGLE_CLIENT_ID).'))
  // iPhone home-screen app: Google's popup can't report back here, so sign in on
  // Google's full page in Safari and pick the session up with a one-time code
  // (same flow and storage key as app.js safariSignIn).
  const iphone = /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)
  if (iphone && (matchMedia('(display-mode: standalone)').matches || navigator.standalone === true)) {
    const status = h('p', { class: 'empty' })
    const check = async () => {
      const pending = JSON.parse(localStorage.getItem('recite.handoff') ?? 'null')
      if (!pending || Date.now() > pending.until || document.hidden) return
      const data = await fetch('/api/handoff', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ id: pending.code }) })
        .then(r => r.json()).catch(() => ({}))
      if (data.user) { localStorage.removeItem('recite.handoff'); load() }
    }
    setInterval(check, 2000)
    document.addEventListener('visibilitychange', check)
    slot.append(h('button', {
      class: 'btn',
      onclick() {
        const code = crypto.randomUUID()
        localStorage.setItem('recite.handoff', JSON.stringify({ code, until: Date.now() + 10 * 60 * 1000 }))
        window.open(`https://accounts.google.com/o/oauth2/v2/auth?${new URLSearchParams({
          client_id: googleClientId, redirect_uri: `${location.origin}/api/auth`, response_type: 'id_token',
          response_mode: 'form_post', scope: 'openid email profile', state: code, nonce: code, prompt: 'select_account',
        })}`, '_blank')
        status.textContent = 'Finish signing in on the Safari page that opened, then come back here.'
      },
    }, 'Sign in with Google'), status)
    return
  }
  const script = h('script', { src: 'https://accounts.google.com/gsi/client', async: true })
  script.onload = () => {
    google.accounts.id.initialize({
      client_id: googleClientId,
      callback: async ({ credential }) => {
        await fetch('/api/auth', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ credential }) })
        load()
      },
    })
    google.accounts.id.renderButton(slot, { theme: 'filled_blue', size: 'large', shape: 'pill' })
  }
  document.head.append(script)
}

let view = 'recitations'
let surahNames = {}
const namesReady = fetch('/quran.json').then(r => r.json()).then(q => { surahNames = Object.fromEntries(q.map(s => [s.number, s.name])) }).catch(() => {})
const surahName = n => (surahNames[n] ? `${n} · ${surahNames[n]}` : `Surah ${n}`)
const CONTEXTS = { browser: '🌐 Browser', 'in-app': '📱 In-app browser', pwa: '🏠 Home screen' }
const PAGE_NAMES = { home: '📖 Recite (home)', surah: '🕌 A surah', story: '🌙 Story', support: '💝 Support' }

function render(data) {
  const t = data.totals
  const kpi = (value, label) => h('div', { class: 'kpi' }, h('b', {}, value ?? 0), h('span', {}, label))
  const views = {
    recitations: ['Recitations', table([['Who'], ['Surah'], ['Mode'], ['Score', true], ['Stars'], ['When', true]],
      data.recitations.map(r => h('tr', {}, h('td', {}, who(r.email)), h('td', {}, surahName(r.surah)),
        h('td', {}, r.mode === 'surah' ? '🕌 Whole surah' : `📖 Verse ${r.verse ?? ''}`),
        h('td', { class: 'num' }, r.score), h('td', { class: 'stars-cell', 'aria-label': `${r.stars} stars` }, '★'.repeat(r.stars) || '—'),
        h('td', { class: 'num' }, when(r.created_at)))))],
    surahs: ['Surahs', table([['Surah'], ['Views', true], ['Viewers', true], ['Recitations', true], ['Whole surah', true], ['Avg score', true], ['Avg ★', true]],
      data.surahs.map(r => h('tr', {}, h('td', {}, surahName(r.surah)), h('td', { class: 'num' }, r.views ?? 0), h('td', { class: 'num' }, r.viewers ?? 0),
        h('td', { class: 'num' }, r.recitations ?? 0), h('td', { class: 'num' }, r.whole_surah ?? 0),
        h('td', { class: 'num' }, r.avg_score ?? '—'), h('td', { class: 'num' }, r.avg_stars ?? '—'))))],
    pages: ['Pages', table([['Page'], ['Views', true], ['Visitors', true], ['Signed in', true]],
      data.pages.map(r => h('tr', {}, h('td', {}, PAGE_NAMES[r.page] ?? r.page), h('td', { class: 'num' }, r.views),
        h('td', { class: 'num' }, r.viewers), h('td', { class: 'num' }, r.signed_in))))],
    logins: ['Sign-ins', table([['Who'], ['Name'], ['When', true]],
      data.logins.map(l => h('tr', {}, h('td', {}, l.email), h('td', {}, l.name ?? ''), h('td', { class: 'num' }, when(l.created_at)))))],
    ratings: ['Reviews', table([['Stars'], ['Review'], ['Who'], ['When', true]],
      data.ratings.map(r => h('tr', {}, h('td', { class: 'stars-cell', 'aria-label': `${r.stars} stars` }, '★'.repeat(r.stars)),
        h('td', {}, r.text ?? h('span', { class: 'guest' }, '—')), h('td', {}, who(r.email)), h('td', { class: 'num' }, when(r.created_at)))))],
    signin: ['Sign-in attempts', [
      h('p', { class: 'empty' }, 'Each tap on "Sign in with Google": finished, failed, or abandoned (the Google window closed or never came back). In-app = an app\'s built-in browser (Instagram, Facebook, TikTok…); home screen = the installed app.'),
      table([['Browser'], ['Where'], ['Taps', true], ['Finished', true], ['Failed', true], ['Abandoned', true], ['Success', true], ['Devices', true]],
        data.signinBrowsers.map(r => h('tr', {}, h('td', {}, r.browser), h('td', {}, CONTEXTS[r.context] ?? r.context),
          h('td', { class: 'num' }, r.taps), h('td', { class: 'num' }, r.done), h('td', { class: 'num' }, r.failed),
          h('td', { class: 'num' }, Math.max(0, r.taps - r.done - r.failed)),
          h('td', { class: 'num' }, r.taps ? `${Math.round((100 * r.done) / r.taps)}%` : '—'), h('td', { class: 'num' }, r.devices)))),
      h('h3', {}, 'Latest attempts'),
      table([['Result'], ['Browser'], ['Where'], ['Who'], ['When', true]],
        data.signinAttempts.map(a => h('tr', {}, h('td', {}, a.done ? '✅ Finished' : a.failed ? '❌ Failed' : '⏳ Abandoned'),
          h('td', {}, a.browser), h('td', {}, CONTEXTS[a.context] ?? a.context), h('td', {}, who(a.email)), h('td', { class: 'num' }, when(a.created_at))))),
    ]],
    taps: ['Donate & share taps', table([['Tap'], ['Who'], ['When', true]],
      data.taps.map(e => h('tr', {}, h('td', {}, e.type === 'donate_tap' ? '💝 Donate' : '🔗 Share'), h('td', {}, who(e.email)), h('td', { class: 'num' }, when(e.created_at)))))],
  }
  const [title, body] = views[view]
  $dash.replaceChildren(
    h('header', {}, h('h1', {}, 'Ayok Ngaji · Analytics'), h('p', { class: 'sub' }, `Updated ${when(Date.now())}`)),
    h('div', { class: 'kpis' },
      kpi(t.users, 'Accounts'), kpi(t.new_users_7d, 'New this week'), kpi(t.active_7d, 'Signed in this week'),
      kpi(t.rating_avg ? `${t.rating_avg} ★` : '—', `Rating · ${t.ratings} reviews`), kpi(t.donate_taps, 'Donate taps'), kpi(t.shares, 'Shares'),
      kpi(t.visitors_7d, 'Visitors this week'), kpi(t.views_7d, 'Page views this week'), kpi(t.recitations_7d, 'Recitations this week'),
      kpi(t.whole_surah_7d, 'Whole-surah this week'), kpi(t.avg_score_7d ?? '—', 'Avg score this week')),
    h('section', { class: 'card' }, h('h2', {}, 'Sign-ins per day'), chart(data.daily)),
    h('div', { class: 'tabs-row', role: 'group' }, Object.entries(views).map(([id, [label]]) =>
      h('button', { 'aria-pressed': String(id === view), onclick: () => { view = id; render(data) } }, label))),
    h('section', { class: 'card' }, h('h2', {}, title), body))
}

async function load() {
  const res = await fetch('/api/analytics').catch(() => null)
  if (!res) return signInGate('Could not reach the server.')
  if (res.status === 401) return signInGate('Sign in with the owner account.')
  if (res.status === 403) return signInGate('This account is not an admin. Sign in with the owner account.')
  const data = await res.json()
  await namesReady
  render(data)
}

load()
