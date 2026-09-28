// Sign in → save stars and position → read them back → rate → sign out,
// against an in-memory database and a stand-in for Google's signing keys.
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createLocalJWKSet, exportJWK, generateKeyPair, SignJWT } from 'jose'

process.env.TURSO_DATABASE_URL = ':memory:'
process.env.GOOGLE_CLIENT_ID = 'test-client'
process.env.SESSION_SECRET = 'test-secret-that-is-long-enough-for-hs256'

const { google } = await import('../api/_lib.js')
const auth = await import('../api/auth.js')
const me = await import('../api/me.js')
const progress = await import('../api/progress.js')
const rating = await import('../api/rating.js')
const event = await import('../api/event.js')
const analytics = await import('../api/analytics.js')
const track = await import('../api/track.js')

const { publicKey, privateKey } = await generateKeyPair('RS256')
google.keys = createLocalJWKSet({ keys: [{ ...(await exportJWK(publicKey)), kid: 'k', alg: 'RS256' }] })
const idToken = (claims, audience = 'test-client', sub = 'google-123') => new SignJWT({ email_verified: true, ...claims })
  .setProtectedHeader({ alg: 'RS256', kid: 'k' }).setIssuer('https://accounts.google.com')
  .setAudience(audience).setSubject(sub).setExpirationTime('5m').sign(privateKey)

const req = (method, bodyData, cookie) => new Request('http://localhost/api', {
  method, headers: { 'content-type': 'application/json', ...(cookie && { cookie }) },
  body: bodyData && JSON.stringify(bodyData),
})

test('a token for another app is refused', async () => {
  const res = await auth.POST(req('POST', { credential: await idToken({ email: 'a@b.c' }, 'someone-else') }))
  assert.equal(res.status, 401)
})

test('signed out: nothing is saved', async () => {
  assert.deepEqual(await (await me.GET(req('GET'))).json(), { user: null })
  assert.equal((await progress.PUT(req('PUT', { stars: { '1:1': 3 } }))).status, 401)
})

test('sign in, save, come back to the same state', async () => {
  const res = await auth.POST(req('POST', { credential: await idToken({ email: 'kid@example.com', name: 'Kid' }) }))
  assert.equal(res.status, 200)
  const cookie = res.headers.get('set-cookie').split(';')[0]
  assert.match(cookie, /^rq_session=/)

  await progress.PUT(req('PUT', { stars: { '1:1': 2, '112:1': 3, 'bad': 3, '1:2': 9 }, state: { surah: 112, verse: 2, whole: false, lang: 'id' } }, cookie))
  await progress.PUT(req('PUT', { stars: { '1:1': 1 } }, cookie)) // a worse try elsewhere
  const data = await (await me.GET(req('GET', null, cookie))).json()
  assert.equal(data.user.email, 'kid@example.com')
  assert.deepEqual(data.stars, { '1:1': 2, '112:1': 3 })
  assert.deepEqual(data.state, { surah: 112, verse: 2, whole: false, lang: 'id' })

  assert.equal((await rating.POST(req('POST', { stars: 5, text: '  Bagus!  ' }, cookie))).status, 200)
  assert.equal((await rating.POST(req('POST', { stars: 6 }))).status, 400)
  assert.match(auth.DELETE().headers.get('set-cookie'), /Max-Age=0/)
})

test('a forged session cookie is ignored', async () => {
  const data = await (await me.GET(req('GET', null, 'rq_session=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.bad'))).json()
  assert.deepEqual(data, { user: null })
})

test('only an admin can open the dashboard', async () => {
  process.env.ADMIN_EMAILS = 'Owner@Example.com'
  const signIn = async (email, sub) => (await auth.POST(req('POST', { credential: await idToken({ email }, 'test-client', sub) })))
    .headers.get('set-cookie').split(';')[0]
  const owner = await signIn('owner@example.com', 'google-owner')
  const parent = await signIn('parent@example.com', 'google-parent')

  assert.equal((await event.POST(req('POST', { type: 'donate_tap' }, parent))).status, 200)
  assert.equal((await event.POST(req('POST', { type: 'share' }))).status, 200)
  assert.equal((await event.POST(req('POST', { type: 'anything' }))).status, 400)

  assert.equal((await analytics.GET(req('GET'))).status, 401)
  assert.equal((await analytics.GET(req('GET', null, parent))).status, 403)
  const res = await analytics.GET(req('GET', null, owner))
  assert.equal(res.status, 200)
  const data = await res.json()
  assert.equal(data.totals.donate_taps, 1)
  assert.equal(data.totals.shares, 1)
  assert.ok(data.logins.some(l => l.email === 'parent@example.com'))
  assert.equal(data.taps.find(t => t.type === 'donate_tap').email, 'parent@example.com')
  assert.equal(data.taps.find(t => t.type === 'share').email, null) // a guest
  assert.ok(data.ratings.length >= 1)
})


test('page views and recitation grades reach the dashboard', async () => {
  process.env.ADMIN_EMAILS = 'owner@example.com'
  const owner = (await auth.POST(req('POST', { credential: await idToken({ email: 'owner@example.com' }, 'test-client', 'google-owner') })))
    .headers.get('set-cookie').split(';')[0]
  const device = 'dev-guest-12345'
  const post = data => track.POST(req('POST', data))
  assert.equal((await post({ type: 'view', device, page: 'home' })).status, 200)
  assert.equal((await post({ type: 'view', device, page: 'surah', surah: 112 })).status, 200)
  assert.equal((await post({ type: 'recite', device, surah: 112, mode: 'surah', score: 93, stars: 2 })).status, 200)
  assert.equal((await post({ type: 'recite', device, surah: 112, verse: 1, mode: 'verse', score: 100, stars: 3 })).status, 200)
  assert.equal((await post({ type: 'recite', surah: 999, mode: 'verse', score: 1, stars: 1 })).status, 400)
  assert.equal((await post({ type: 'view', page: 'admin' })).status, 400)

  const data = await (await analytics.GET(req('GET', null, owner))).json()
  assert.ok(data.totals.views_7d >= 2)
  assert.ok(data.totals.whole_surah_7d >= 1)
  const ikhlas = data.surahs.find(r => r.surah === 112)
  assert.equal(ikhlas.recitations, 2)
  assert.equal(ikhlas.whole_surah, 1)
  assert.equal(ikhlas.avg_score, 97)
  assert.equal(data.recitations[0].mode, 'verse')
  assert.ok(data.pages.some(p => p.page === 'home'))
})
