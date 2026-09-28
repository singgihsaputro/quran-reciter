// Everything the app needs to start must be in the service worker's CORE list,
// or the app won't open while the server is down (see public/sw.js).
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

const read = file => readFileSync(new URL(`../public/${file}`, import.meta.url), 'utf8')

test('the service worker saves every file the app starts with', () => {
  const core = new Set(read('sw.js').match(/const CORE = \[([^\]]+)\]/)[1].match(/'([^']+)'/g).map(p => p.slice(2, -1)))
  const needed = new Set(['index.html'])
  for (const [, file] of read('index.html').matchAll(/(?:src|href)="([^":]+)"/g)) needed.add(file)
  const modules = ['app.js']
  for (const file of modules) {
    for (const [, dep] of read(file).matchAll(/from '\.\/([^']+)'/g)) if (!modules.includes(dep)) modules.push(dep)
    for (const [, data] of read(file).matchAll(/'([a-z_]+\.json)'/g)) needed.add(data)
  }
  for (const file of modules) needed.add(file)
  needed.delete('index.html') // saved as '/'
  const missing = [...needed].filter(file => !core.has(file))
  assert.deepEqual(missing, [])
  assert.ok(core.has('') && core.has('500.html') && core.has('qris-ayok-ngaji.jpg'))
})
