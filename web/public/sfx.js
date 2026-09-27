// Game sound effects, synthesised with Web Audio: nothing to download, nothing
// to license. Soft and short — a nudge, never a buzzer — and silent whenever
// `sfx.on` is false (the switch in the Support tab).

let ctx = null

// Browsers only start audio from a tap; the first tap anywhere wakes it.
document.addEventListener('pointerdown', () => {
  try {
    ctx ??= new (window.AudioContext || window.webkitAudioContext)()
    if (ctx.state === 'suspended') ctx.resume()
  } catch { /* no Web Audio: stay silent */ }
}, true)

/** One note: frequency (Hz), start (s from now), length (s). */
function note(freq, start = 0, length = 0.18, { type = 'sine', gain = 0.14, slideTo = null } = {}) {
  if (!sfx.on || !ctx) return
  const t = ctx.currentTime + start
  const osc = ctx.createOscillator()
  const amp = ctx.createGain()
  osc.type = type
  osc.frequency.setValueAtTime(freq, t)
  if (slideTo) osc.frequency.exponentialRampToValueAtTime(slideTo, t + length)
  amp.gain.setValueAtTime(0.0001, t)
  amp.gain.exponentialRampToValueAtTime(gain, t + 0.012) // no click at the start
  amp.gain.exponentialRampToValueAtTime(0.0001, t + length)
  osc.connect(amp).connect(ctx.destination)
  osc.start(t)
  osc.stop(t + length + 0.02)
}

const C5 = 523.25, E5 = 659.25, G5 = 783.99, C6 = 1046.5, E6 = 1318.5, G6 = 1568

export const sfx = {
  on: true,

  /** A button press. */
  tap() { note(520, 0, 0.07, { type: 'triangle', gain: 0.09, slideTo: 880 }) },

  /** The i-th star popping in (0, 1, 2): each one a step higher. */
  star(i, delay = 0) { note([C6, E6, G6][i] ?? G6, delay, 0.3, { gain: 0.12 }); note(([C6, E6, G6][i] ?? G6) * 2, delay, 0.18, { gain: 0.04 }) },

  /** Three stars: a little fanfare. */
  win(delay = 0) {
    ;[C5, E5, G5, C6].forEach((f, i) => note(f, delay + i * 0.09, 0.16, { type: 'triangle', gain: 0.12 }))
    ;[C6, E6, G6].forEach(f => note(f, delay + 0.4, 0.55, { type: 'triangle', gain: 0.07 }))
  },

  /** One or two stars: a cheerful ding-ding. */
  good(delay = 0) { note(G5, delay, 0.16, { gain: 0.12 }); note(C6, delay + 0.14, 0.26, { gain: 0.12 }) },

  /** No stars: a gentle "let's try again", falling softly. */
  tryAgain(delay = 0) { note(E5, delay, 0.2, { gain: 0.1 }); note(C5, delay + 0.18, 0.32, { gain: 0.1 }) },
}
