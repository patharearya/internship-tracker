# Music and effects as one piece: 96 BPM, A minor -> C major, shared reverb. Writes audio.wav (48 kHz stereo).
import numpy as np, wave

SR, DUR = 48000, 21.5
N = int(SR * DUR)
t = np.arange(N) / SR
BEAT = 60 / 96
hz = lambda m: 440 * 2 ** ((m - 69) / 12)

def env(n, a, r):
    e = np.ones(n); ai = int(a * SR); e[:ai] = np.linspace(0, 1, ai)
    e *= np.exp(-np.arange(n) / SR / r); return e

def add(buf, start, sig, gain):
    i = int(start * SR); j = min(N, i + len(sig)); buf[i:j] += sig[:j - i] * gain

def lowpass(x, cut):
    f = np.fft.rfft(x); fr = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(f / np.sqrt(1 + (fr / cut) ** 4), len(x))

pad, pluck, low, fx = (np.zeros(N) for _ in range(4))

# chords (MIDI), change every 4 beats; resolve to Cmaj9 for the outro at 18.6s
CH = [[57, 60, 64, 71], [53, 57, 60, 64], [48, 55, 60, 64], [55, 59, 62, 67]]
times = [i * 4 * BEAT for i in range(8)] + [18.6]
seq = [CH[i % 4] for i in range(8)] + [[48, 55, 59, 62, 64]]
for k, (st, ch) in enumerate(zip(times, seq)):
    en = times[k + 1] if k + 1 < len(times) else DUR
    n = int((en - st + 1.2) * SR); tt = np.arange(n) / SR
    e = np.minimum(1, tt / .5) * np.clip(1 - (tt - (en - st)) / 1.2, 0, 1)
    for m in ch:
        for d in (-.07, .07):  # detuned saws, softened later by a low-pass
            ph = (hz(m) * (1 + d / 100) * tt) % 1
            add(pad, st, (2 * ph - 1) * e, .05)
    add(low, st, np.sin(2 * np.pi * hz(ch[0] - 12) * tt) * e, .18)

# pluck arpeggio in eighths, entering softly
step = BEAT / 2
for i in range(int(DUR / step)):
    st = i * step
    if st < .5 or st > 20.6: continue
    k = min(sum(1 for x in times if x <= st) - 1, len(seq) - 1)
    ch = seq[k]; m = ch[[0, 2, 1, 3, 2, 1, 3, 2][i % 8] % len(ch)] + 12
    n = int(.6 * SR); tt = np.arange(n) / SR
    s = (np.sin(2 * np.pi * hz(m) * tt) + .3 * np.sin(4 * np.pi * hz(m) * tt)) * env(n, .004, .18)
    add(pluck, st, s, .11 * min(1, st / 3) * (.75 if i % 2 else 1))

# soft pulse on the beat once the reveal lands; pad ducks under it
duck = np.ones(N)
b = 3.2
while b < 20.8:
    n = int(.35 * SR); tt = np.arange(n) / SR
    f = 52 + 60 * np.exp(-tt * 35)
    add(low, b, np.sin(2 * np.pi * np.cumsum(f) / SR) * env(n, .002, .09), .34)
    i = int(b * SR); j = min(N, i + n); duck[i:j] = np.minimum(duck[i:j], 1 - .35 * np.exp(-tt[:j - i] * 9))
    b += BEAT

# effects, in key: airy swells into each scene, plucked clicks, a closing bell
rng = np.random.default_rng(3)
for s in (3.2, 7.0, 11.4, 15.4, 18.6):
    n = int(.9 * SR); tt = np.arange(n) / SR
    sw = lowpass(rng.standard_normal(n), 2500) * (tt / .9) ** 2 * np.exp(-np.maximum(0, tt - .8) * 30)
    add(fx, s - .8, sw, .05)
for c in (8.65, 13.2):
    n = int(.4 * SR); tt = np.arange(n) / SR
    add(fx, c, (np.sin(2 * np.pi * hz(88) * tt) + .4 * np.sin(2 * np.pi * hz(95) * tt)) * env(n, .002, .06), .07)
for m, d in ((84, 0), (88, .09), (91, .18)):
    n = int(2.6 * SR); tt = np.arange(n) / SR
    add(fx, 18.9 + d, (np.sin(2 * np.pi * hz(m) * tt) + .2 * np.sin(2 * np.pi * hz(m) * 2.76 * tt)) * env(n, .003, .7), .05)

dry = lowpass(pad, 1600) * duck + pluck + lowpass(low, 400) + fx

# shared reverb: exponential noise tail, decorrelated per channel
def verb(x, seed):
    n = int(2.2 * SR); r = np.random.default_rng(seed).standard_normal(n) * np.exp(-np.arange(n) / SR / .45)
    r = lowpass(r, 5000); L = len(x) + n
    return np.fft.irfft(np.fft.rfft(x, L) * np.fft.rfft(r, L), L)[:len(x)] * .012
send = lowpass(pad, 1600) * .4 + pluck + fx * 1.4
mixL, mixR = dry + verb(send, 1), dry + verb(send, 2)

st = np.stack([mixL, mixR], 1)
fade = np.clip((DUR - t) / 1.2, 0, 1)[:, None] * np.clip(t / .08, 0, 1)[:, None]
st = np.tanh(st * fade * 1.6) / np.tanh(1.6)
st *= .89 / np.abs(st).max()
with wave.open("audio.wav", "wb") as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes((st * 32767).astype("<i2").tobytes())
print("peak", np.abs(st).max(), "rms dB", 20 * np.log10(np.sqrt((st ** 2).mean())))
