# "Lagu Alif Ba Ta – Ayok Ngaji": an original tune (everything synthesised here)
# with the 28 recorded letters (public/hijaiyah, GFDL) sung on the beat.
# Run from a folder holding NN.wav copies of public/hijaiyah/NN.mp3 (44.1 kHz mono),
# with numpy; then: ffmpeg -i song.wav -af loudnorm=I=-16:TP=-1.5:LRA=9 -b:a 96k lagu-alif-ba-ta.mp3.
# Its timing (intro, step) is copied into SONG in public/app.js.
import json, wave, numpy as np
SR = 44100
BPM = 100
BEAT = 60 / BPM
STEP = 2 * BEAT                      # one letter every two beats
INTRO = 4 * 2 * BEAT                 # two bars before the first letter
ORDER = list(range(27)) + [29]       # card indices with a recording, in the cards' order
N_LET = len(ORDER)
OUTRO = 2 * 4 * BEAT
DUR = INTRO + N_LET * STEP + OUTRO
N = int(DUR * SR)
music = np.zeros(N); voice = np.zeros(N)
t = np.arange(N) / SR
f = lambda m: 440 * 2 ** ((m - 69) / 12)

def add(buf, start, sig):
    a = int(start * SR); b = min(N, a + len(sig))
    if a < N: buf[a:b] += sig[:b - a]

def tone(freq, length, gain, attack=0.01, decay=3.0, partials=((1, 1),)):
    n = int(length * SR); tt = np.arange(n) / SR
    env = np.minimum(1, tt / attack) * np.exp(-decay * tt / length)
    return gain * env * sum(w * np.sin(2 * np.pi * freq * k * tt) for k, w in partials)

def pad(freq, length, gain):
    n = int(length * SR); tt = np.arange(n) / SR
    env = np.minimum(1, tt / 0.3) * np.minimum(1, (length - tt) / 0.3)
    return gain * env * (np.sin(2 * np.pi * freq * tt) + 0.4 * np.sin(4 * np.pi * freq * tt) + 0.15 * np.sin(6 * np.pi * freq * tt))

rng = np.random.default_rng(3)
def tick(gain):  # soft shaker
    n = int(0.06 * SR); return gain * rng.standard_normal(n) * np.exp(-np.arange(n) / (0.012 * SR))

# C – G – Am – F, one chord a bar.
CHORDS = [(60, 64, 67), (55, 59, 62), (57, 60, 64), (53, 57, 60)]
ROOTS = [36, 43, 45, 41]
BELL = [[76, 79, 84, 79], [74, 79, 83, 79], [76, 81, 84, 81], [77, 81, 84, 81]]
bars = int(np.ceil(DUR / (4 * BEAT)))
for bar in range(bars):
    t0 = bar * 4 * BEAT; c = bar % 4
    last = bar == bars - 1
    for m in CHORDS[c]: add(music, t0, pad(f(m), 4 * BEAT, 0.045))
    for b in (0, 2): add(music, t0 + b * BEAT, tone(f(ROOTS[c]), BEAT * 1.6, 0.16, attack=0.005, decay=4, partials=((1, 1), (2, .3))))
    if last: continue
    for b in range(4):
        add(music, t0 + b * BEAT, tick(0.03 if b % 2 else 0.05))
        add(music, t0 + (b + .5) * BEAT, tick(0.02))
        # Bells on the off-beats, so the letters on beats 1 and 3 stay clear.
        if b % 2 == 1 or t0 < INTRO:
            add(music, t0 + b * BEAT, tone(f(BELL[c][b]), 0.6, 0.07, attack=0.003, decay=5, partials=((1, 1), (2.76, .3), (5.4, .1))))

# The letters, one every two beats, each starting just on its beat.
starts = []
for k, card in enumerate(ORDER):
    with wave.open(f'{card:02d}.wav') as w:
        clip = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(float) / 32768
    at = INTRO + k * STEP
    starts.append(round(at, 3))
    add(voice, at - 0.08, clip * 0.9)  # the recordings carry ~0.1 s of air before the sound
# A sparkle to finish.
for i, m in enumerate((84, 88, 91, 96)): add(music, INTRO + N_LET * STEP + i * 0.1, tone(f(m), 1.2, 0.07, attack=0.003, decay=4, partials=((1, 1), (2, .3))))

# Duck the music a little under the voice.
env = np.convolve(np.abs(voice), np.ones(2205) / 2205, mode='same')
duck = 1 - 0.45 * np.clip(env / (env.max() or 1) * 3, 0, 1)
mix = music * duck + voice
fade = int(1.5 * SR); mix[-fade:] *= np.linspace(1, 0, fade)
mix = mix / np.abs(mix).max() * 0.9
with wave.open('song.wav', 'wb') as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes((mix * 32767).astype(np.int16).tobytes())
json.dump({'intro': round(INTRO, 3), 'step': round(STEP, 3), 'order': ORDER, 'duration': round(DUR, 2)}, open('timing.json', 'w'))
print(f'{DUR:.1f}s, intro {INTRO:.2f}s, a letter every {STEP:.2f}s')
