# Ayok Ngaji — web

The app for Safari and Chrome on iPhone and Chrome on Android, with Google
sign-in and saved progress. Static files in `public/`, API routes in `api/`,
data in Turso. Hosted on Vercel with this folder as the project root.

Signed out, Al-Fatihah, Al-Ikhlas, Al-Falaq and An-Nas are open and stars stay
in the browser. Signed in, every surah opens, and stars (best try wins) plus
the last verse and language are saved to the account.

## API

| Route | |
|---|---|
| `GET /api/config` | public settings (the Google client id) |
| `POST /api/auth` | `{ credential }` from Google Identity Services → session cookie |
| `POST /api/otp` | `{ email }` → emails a 6-digit code (1 a minute, 5 an hour) |
| `PUT /api/otp` | `{ email, code }` → session cookie; the code works once, 10 minutes, 5 tries |
| `DELETE /api/auth` | sign out |
| `GET /api/me` | user, stars, last state |
| `PUT /api/progress` | `{ stars, state }` — stars only ever go up |
| `POST /api/rating` | `{ stars: 1..5, text? }` — signed in or not |
| `POST /api/event` | `{ type: 'donate_tap' \| 'share' }` — counted for the dashboard |
| `GET /api/analytics` | the owner's dashboard data — only for emails in `ADMIN_EMAILS` |

## Email sign-in codes

For families without a Google account. One email is one account, whichever way
it signs in. Codes are stored only as a keyed hash.

Codes are sent through [Brevo](https://www.brevo.com) (free, 300 a day, no
domain needed): sign up, verify a sender address under *Senders*, create an
API key, and set `BREVO_API_KEY`, `EMAIL_FROM` (that verified sender) and
optionally `EMAIL_FROM_NAME` in Vercel. Change `EMAIL_FROM` any time. Locally,
without a key, codes are printed to the `npm run dev` log.

Stars are kept per account on each device, and separately for guests, so
siblings sharing a phone never mix progress.

## Dashboard

`/analytics` shows sign-ins (who, when), reviews, and taps on Donate and
Share, with a 30-day chart. The page is public; its data is not — the API
answers only accounts whose email is in `ADMIN_EMAILS` (comma-separated), so
set that variable in Vercel alongside the others.

The Google ID token is verified on the server against Google's keys and this
app's client id; the session is an HttpOnly cookie signed with `SESSION_SECRET`.

## Run locally

```bash
npm install
cp .env.example .env.local   # fill in SESSION_SECRET; GOOGLE_CLIENT_ID for sign-in
npm run dev                  # http://localhost:3100
npm test                     # the API against an in-memory database
```

## Deploy

1. **Turso** — create a database (region near your users, e.g. Singapore),
   then copy its URL (`libsql://…`) and create an auth token.
2. **Google** — Google Cloud Console → APIs & Services → OAuth consent screen
   (External), then Credentials → Create OAuth client ID → *Web application*.
   Authorized JavaScript origins: your Vercel URL and `http://localhost:3100`.
   No redirect URI is needed.
3. **Vercel** — import the `quran-reciter` repo, set **Root Directory** to
   `web`, framework *Other*, and add the variables from `.env.example`.
4. Add the Vercel URL to the Google client's origins if it wasn't known yet.

Tables are created on the first request.

## Before sharing widely

The users are children. Children under 13 can only have a Google account
through Family Link, so in practice a parent signs in. Publish a short privacy
policy (the app keeps the Google email, name, stars, last verse and ratings)
before listing it anywhere.
