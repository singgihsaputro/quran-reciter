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

const { publicKey, privateKey } = await generateKeyPair('RS256')
google.keys = createLocalJWKSet({ keys: [{ ...(await exportJWK(publicKey)), kid: 'k', alg: 'RS256' }] })
const idToken = (claims, audience = 'test-client') => new SignJWT({ email_verified: true, ...claims })
  .setProtectedHeader({ alg: 'RS256', kid: 'k' }).setIssuer('https://accounts.google.com')
  .setAudience(audience).setSubject('google-123').setExpirationTime('5m').sign(privateKey)

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
