// Local stand-in for Vercel: serves public/ and runs api/*.js the way Vercel
// does (exported GET/POST/PUT/DELETE taking a Request). `npm run dev`, with a
// .env.local holding the variables listed in .env.example.
import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import { extname, join, normalize } from 'node:path'

const PORT = Number(process.env.PORT ?? 3100)
const TYPES = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json',
  '.png': 'image/png', '.webmanifest': 'application/manifest+json',
}

createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`)
  try {
    if (url.pathname.startsWith('/api/')) {
      const name = url.pathname.slice(5).replace(/\/$/, '')
      const route = /^[a-z]+$/.test(name) ? await import(`./api/${name}.js`).catch(() => null) : null
      const handler = route?.[req.method]
      if (!handler) return res.writeHead(route ? 405 : 404).end()
      const chunks = []
      for await (const chunk of req) chunks.push(chunk)
      const response = await handler(new Request(url, {
        method: req.method,
        headers: Object.entries(req.headers).map(([k, v]) => [k, String(v)]),
        body: chunks.length ? Buffer.concat(chunks) : undefined,
      }))
      res.writeHead(response.status, Object.fromEntries(response.headers))
      return res.end(Buffer.from(await response.arrayBuffer()))
    }
    const file = normalize(join('public', url.pathname === '/' ? 'index.html' : url.pathname))
    if (!file.startsWith('public')) return res.writeHead(403).end()
    const content = await readFile(file)
    res.writeHead(200, { 'content-type': TYPES[extname(file)] ?? 'application/octet-stream' })
    res.end(content)
  } catch (err) {
    if (err.code === 'ENOENT') return res.writeHead(404).end()
    console.error(err)
    res.writeHead(500).end()
  }
}).listen(PORT, () => console.log(`Recite Quest on http://localhost:${PORT}`))
