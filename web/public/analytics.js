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

let view = 'logins'

function render(data) {
  const t = data.totals
  const kpi = (value, label) => h('div', { class: 'kpi' }, h('b', {}, value ?? 0), h('span', {}, label))
  const views = {
    logins: ['Sign-ins', table([['Who'], ['Name'], ['When', true]],
      data.logins.map(l => h('tr', {}, h('td', {}, l.email), h('td', {}, l.name ?? ''), h('td', { class: 'num' }, when(l.created_at)))))],
    ratings: ['Reviews', table([['Stars'], ['Review'], ['Who'], ['When', true]],
      data.ratings.map(r => h('tr', {}, h('td', { class: 'stars-cell', 'aria-label': `${r.stars} stars` }, '★'.repeat(r.stars)),
        h('td', {}, r.text ?? h('span', { class: 'guest' }, '—')), h('td', {}, who(r.email)), h('td', { class: 'num' }, when(r.created_at)))))],
    taps: ['Donate & share taps', table([['Tap'], ['Who'], ['When', true]],
      data.taps.map(e => h('tr', {}, h('td', {}, e.type === 'donate_tap' ? '💝 Donate' : '🔗 Share'), h('td', {}, who(e.email)), h('td', { class: 'num' }, when(e.created_at)))))],
  }
  const [title, body] = views[view]
  $dash.replaceChildren(
    h('header', {}, h('h1', {}, 'Ayok Ngaji · Analytics'), h('p', { class: 'sub' }, `Updated ${when(Date.now())}`)),
    h('div', { class: 'kpis' },
      kpi(t.users, 'Accounts'), kpi(t.new_users_7d, 'New this week'), kpi(t.active_7d, 'Signed in this week'),
      kpi(t.rating_avg ? `${t.rating_avg} ★` : '—', `Rating · ${t.ratings} reviews`), kpi(t.donate_taps, 'Donate taps'), kpi(t.shares, 'Shares')),
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
  render(await res.json())
}

load()
