// GET: the public settings the page needs. The OAuth client id is not a secret.
import { json } from './_lib.js'

export function GET() {
  return json({ googleClientId: process.env.GOOGLE_CLIENT_ID ?? null })
}
