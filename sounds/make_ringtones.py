# Bants call ringtones (chiptune). The app uses "Powerup ring": 3_powerup_ring_VOICE / _VIDEO,
# saved as res/raw/ringtone_voice.ogg and ringtone_video.ogg. Run: python3 make_ringtones.py
import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, lfilter
SR = 44100
rng = np.random.default_rng(3)
def t(d): return np.arange(int(SR*d))/SR
def canvas(d): return np.zeros(int(SR*d))
def place(buf, sig, at):
    i = int(at*SR); n = min(len(sig), len(buf)-i)
    if n > 0: buf[i:i+n] += sig[:n]
def lp(s, f): b, a = butter(2, f/(SR/2)); return lfilter(b, a, s)
def hp(s, f): b, a = butter(2, f/(SR/2), 'high'); return lfilter(b, a, s)
def midi(n): return 440*2**((n-69)/12)

# NES-style voices
def pulse(n, d, duty=0.25, decay=3.0, vib=0.0):
    f = midi(n); x = t(d)
    ph = (np.cumsum(f*(1 + vib*np.sin(2*np.pi*6*x)*np.minimum(1, x/0.15)))/SR) % 1
    s = np.where(ph < duty, 1.0, -1.0)
    env = np.exp(-x*decay)*np.minimum(1, x/0.003)*np.minimum(1, (d-x)/0.01)
    return s*env
def tri(n, d):
    f = midi(n); x = t(d); ph = (f*x) % 1
    s = 4*np.abs(ph-0.5)-1
    return s*np.minimum(1, x/0.003)*np.minimum(1, (d-x)/0.01)
def hat(d=0.04):
    x = t(d); return hp(rng.standard_normal(len(x)), 6000)*np.exp(-x*120)
def kick():
    x = t(0.12); f = 50 + 120*np.exp(-x*40)
    return np.sin(2*np.pi*np.cumsum(f)/SR)*np.exp(-x*25)

def render(o, name, gain=0.55):
    o = lp(o, 9000); o = o - o.mean()
    o = np.tanh(o*1.1); o = o/np.max(np.abs(o))*gain
    f = int(0.01*SR); o[-f:] *= np.linspace(1, 0, f); o[:f] *= np.linspace(0, 1, f)
    wavfile.write(name + ".wav", SR, (o*32767).astype(np.int16))

S = 0.075  # one 16th note at ~200 bpm

# ── 1. Double ring: classic "ring-ring … ring-ring" rhythm, in chip ──
def double_ring(name, notes, bass):
    L = 3.0; o = canvas(L)
    for burst in (0.0, 0.5):
        for k in range(6):  # quick 6-note arpeggio burst = one "ring"
            place(o, pulse(notes[k % len(notes)], 0.07, 0.25, 6), burst + k*0.055)
    place(o, tri(bass, 0.45)*0.6, 0.0); place(o, tri(bass+7, 0.45)*0.6, 0.5)
    render(o, name)

# ── 2. Chip anthem: a little hook with bass and drums ──
def anthem(name, melody, bass_line, shift=0):
    L = 16*2*S + 0.9; o = canvas(L)  # two bars then a breath
    for step, (n, ln) in enumerate(melody):
        if n is not None: place(o, pulse(n+shift, ln*S*0.95, 0.25, 2.5, vib=0.004), step*S)
    for i, n in enumerate(bass_line):
        place(o, tri(n+shift, 2*S*0.9)*0.7, i*2*S)
    for i in range(16):
        place(o, hat()*0.25, i*2*S + S)
        if i % 4 == 0: place(o, kick()*0.6, i*2*S)
    render(o, name)

# ── 3. Power-up ring: the arpeggio you liked, as a call-and-answer phrase ──
def powerup_ring(name, base):
    L = 3.2; o = canvas(L)
    up = [0, 4, 7, 12, 7, 12, 16, 19]
    for i, s in enumerate(up): place(o, pulse(base+s, 0.09 if i < 7 else 0.32, 0.25, 9 if i < 7 else 4), i*0.07)
    ans = [19, 16, 12, 16, 19, 24]
    for i, s in enumerate(ans): place(o, pulse(base+s, 0.09 if i < 5 else 0.36, 0.125, 9 if i < 5 else 3.5), 0.85 + i*0.07)
    for i, s in enumerate([0, 0, 5, 7]): place(o, tri(base-12+s, 0.3)*0.6, i*0.42)
    render(o, name)

# Video calls: bright, rising. Voice calls: same family, a bit lower and calmer.
double_ring("1_double_ring_VIDEO", [72, 76, 79, 84, 79, 84], 48)
double_ring("1_double_ring_VOICE", [76, 79, 76, 79, 76, 79], 45)

melA = [(72,1),(None,1),(76,1),(79,1),(84,2),(None,1),(79,1),(81,1),(79,1),(76,1),(None,1),(79,3),(None,1),
        (72,1),(None,1),(76,1),(79,1),(84,2),(None,1),(88,1),(86,2),(84,1),(79,1),(84,4)]
melB = [(76,1),(None,1),(76,1),(79,1),(76,2),(None,1),(72,1),(74,1),(76,1),(74,1),(None,1),(72,3),(None,1),
        (76,1),(None,1),(76,1),(79,1),(81,2),(None,1),(79,1),(76,2),(74,1),(72,1),(76,4)]
anthem("2_chip_anthem_VIDEO", melA, [48,48,52,52,53,53,55,55, 48,48,52,52,53,55,48,48])
anthem("2_chip_anthem_VOICE", melB, [45,45,48,48,50,50,52,52, 45,45,48,48,50,52,45,45], shift=0)

powerup_ring("3_powerup_ring_VIDEO", 72)
powerup_ring("3_powerup_ring_VOICE", 67)
