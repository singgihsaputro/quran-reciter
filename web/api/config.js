// GET: the public settings the page needs. The OAuth client id and the push
// public key are not secrets.
import { json } from './_lib.js'

export function GET() {
  return json({ googleClientId: process.env.GOOGLE_CLIENT_ID ?? null, vapidPublicKey: process.env.VAPID_PUBLIC_KEY ?? null })
}
