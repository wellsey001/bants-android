import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, lfilter
SR = 44100

def t(d): return np.arange(int(SR*d))/SR

def glide(f0, f1, d, curve=1.0):
    x = t(d); k = (x/d)**curve
    f = f0 + (f1-f0)*k
    return 2*np.pi*np.cumsum(f)/SR

def env(d, a=0.005, decay=8.0):
    x = t(d)
    e = np.exp(-decay*x)
    att = np.minimum(1, x/a)
    return e*att

def tone(phase, bright=0.25):
    # sine with a touch of 2nd/3rd harmonic: rounded but toy-like
    return np.sin(phase) + bright*np.sin(2*phase) + bright*0.4*np.sin(3*phase)

def place(buf, sig, at):
    i = int(at*SR); buf[i:i+len(sig)] += sig[:max(0, len(buf)-i)]

def finish(sig, name, gain=0.85):
    sig = sig - np.mean(sig)
    # soft clip for a bit of punch
    sig = np.tanh(sig*1.3)
    sig = sig/np.max(np.abs(sig))*gain
    fade = int(0.01*SR); sig[-fade:] *= np.linspace(1,0,fade)
    wavfile.write(f"{name}.wav", SR, (sig*32767).astype(np.int16))

# 1. Message notification: "bloop-bop" — a bubbly rising bloop, then a bright pop.
def message():
    out = np.zeros(int(SR*0.6))
    d1 = 0.16
    s1 = tone(glide(380, 820, d1, 0.6), 0.3) * env(d1, 0.004, 14)
    d2 = 0.30
    ph = glide(1250, 1180, d2) + 0.25*np.sin(2*np.pi*14*t(d2))  # little wobble
    s2 = tone(ph, 0.18) * env(d2, 0.002, 11)
    place(out, s1, 0.0); place(out, 0.9*s2, 0.11)
    finish(out, "bants_message")

# 2. In-chat incoming: one soft blip.
def incoming():
    d = 0.16
    s = tone(glide(620, 980, d, 0.5), 0.15) * env(d, 0.003, 22)
    finish(s, "bants_incoming", 0.55)

# 3. Sent: quick "fwip" upward flick with a breathy edge.
def sent():
    d = 0.13
    ph = glide(500, 1900, d, 1.6)
    s = np.sin(ph) * env(d, 0.002, 18)
    b, a = butter(2, [1500/(SR/2), 6000/(SR/2)], 'band')
    noise = lfilter(b, a, np.random.default_rng(3).standard_normal(len(s))) * env(d, 0.001, 35) * 0.35
    finish(s + noise, "bants_sent", 0.5)

# 4. Mention: cheeky wolf whistle "fweet-fwoo".
def mention():
    out = np.zeros(int(SR*0.85))
    rng = np.random.default_rng(7)
    def whistle(f0, fpeak, f1, d, up_frac):
        x = t(d); k = x/d
        f = np.where(k < up_frac, f0 + (fpeak-f0)*(k/up_frac)**0.7, fpeak + (f1-fpeak)*((k-up_frac)/(1-up_frac))**1.3)
        ph = 2*np.pi*np.cumsum(f)/SR + 0.08*np.sin(2*np.pi*6*x)
        e = np.minimum(1, x/0.03) * np.minimum(1, (d-x)/0.06)
        sig = np.sin(ph)*e
        b, a = butter(2, [1200/(SR/2), 4500/(SR/2)], 'band')
        breath = lfilter(b, a, rng.standard_normal(len(x)))*0.05*e
        return sig + breath
    place(out, whistle(1000, 2300, 2200, 0.22, 0.85), 0.0)
    place(out, whistle(1000, 2100, 750, 0.45, 0.35), 0.32)
    finish(out, "bants_mention", 0.65)

# 5. Contact request: bouncy marimba "ding-dong" (up-down), like a cartoon doorbell.
def request():
    out = np.zeros(int(SR*0.9))
    def marimba(f, d=0.45):
        x = t(d)
        mod = 1.8*np.exp(-30*x)*np.sin(2*np.pi*f*4*x)
        return (np.sin(2*np.pi*f*x + mod) + 0.2*np.sin(2*np.pi*f*3.9*x)*np.exp(-25*x)) * env(d, 0.002, 7)
    place(out, marimba(784), 0.0)     # G5
    place(out, marimba(988), 0.13)    # B5
    place(out, marimba(659, 0.6), 0.30)  # E5
    finish(out, "bants_request")

for f in (message, incoming, sent, mention, request): f()
