"""Bants sound packs: 4 styles x 5 notification types, all synthesised from scratch.
Run: python3 make_sounds.py   (writes <type>_<style>.wav)"""
import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, lfilter

SR = 44100
rng = np.random.default_rng(11)

def t(d): return np.arange(int(SR*d))/SR
def canvas(d): return np.zeros(int(SR*d))
def place(buf, sig, at):
    i = int(at*SR); n = min(len(sig), len(buf)-i); buf[i:i+n] += sig[:n]
def bp(sig, lo, hi):
    b, a = butter(2, [lo/(SR/2), min(hi, SR/2-100)/(SR/2)], 'band'); return lfilter(b, a, sig)
def finish(sig, name, gain=0.8):
    sig = sig - np.mean(sig); sig = np.tanh(sig*1.2); sig = sig/np.max(np.abs(sig))*gain
    f = int(0.012*SR); sig[-f:] *= np.linspace(1, 0, f)
    wavfile.write(f"{name}.wav", SR, (sig*32767).astype(np.int16))

# ───────── building blocks ─────────
def sq(f, d, decay, duty_mix=0.6):
    x = t(d); s = np.sign(np.sin(2*np.pi*f*x))*duty_mix + np.sin(2*np.pi*f*x)*(1-duty_mix)
    return s*np.exp(-x*decay)*np.minimum(1, x/0.002)
def drop(f0, f1, d=0.09, decay=38):
    x = t(d); f = f1 + (f0-f1)*np.exp(-x*60)
    return np.sin(2*np.pi*np.cumsum(f)/SR)*np.exp(-x*decay)*np.minimum(1, x/0.002)
def squeak(d, f0, f1):
    x = t(d); f = f0 + (f1-f0)*np.sin(np.pi*x/d)
    ph = 2*np.pi*np.cumsum(f)/SR
    return (np.sin(ph + 2.2*np.sin(ph*2.01)) + bp(rng.standard_normal(len(x)), 2000, 6000)*0.15)*np.sin(np.pi*x/d)**0.6
def kazoo(f, d):
    x = t(d); ph = 2*np.pi*np.cumsum(f*(1 + 0.012*np.sin(2*np.pi*7*x)))/SR
    saw = 2*((ph/(2*np.pi)) % 1) - 1
    return (bp(saw, 400, 3500) + 0.3*bp(rng.standard_normal(len(x)), 1500, 4000))*np.minimum(1, x/0.015)*np.minimum(1, (d-x)/0.04)
def tone(f0, f1, d, decay, curve=1.0, bright=0.2):
    x = t(d); f = f0 + (f1-f0)*(x/d)**curve; ph = 2*np.pi*np.cumsum(f)/SR
    return (np.sin(ph) + bright*np.sin(2*ph) + bright*0.4*np.sin(3*ph))*np.exp(-x*decay)*np.minimum(1, x/0.003)
def snap(gain=1.0):
    x = t(0.05)
    return (bp(rng.standard_normal(len(x)), 1800, 7000)*np.exp(-x*90) + np.sin(2*np.pi*1100*x)*np.exp(-x*70)*0.6)*gain
def tok(f=1700, d=0.12):
    x = t(d); return np.sin(2*np.pi*(f + 600*np.exp(-x*80))*x)*np.exp(-x*45)
def marimba(f, d=0.45):
    x = t(d); mod = 1.8*np.exp(-30*x)*np.sin(2*np.pi*f*4*x)
    return (np.sin(2*np.pi*f*x + mod) + 0.2*np.sin(2*np.pi*f*3.9*x)*np.exp(-25*x))*np.exp(-7*x)*np.minimum(1, x/0.002)
def whistle(f0, fpeak, f1, d, up):
    x = t(d); k = x/d
    rise = f0 + (fpeak-f0)*(np.clip(k/up, 0, 1))**0.7
    fall = fpeak + (f1-fpeak)*(np.clip((k-up)/(1-up), 0, 1))**1.3
    f = np.where(k < up, rise, fall)
    ph = 2*np.pi*np.cumsum(f)/SR + 0.08*np.sin(2*np.pi*6*x)
    e = np.minimum(1, x/0.03)*np.minimum(1, (d-x)/0.06)
    return np.sin(ph)*e + bp(rng.standard_normal(len(x)), 1200, 4500)*0.05*e
def honk(f, d):
    x = t(d); ph = 2*np.pi*f*x
    s = np.sign(np.sin(ph))*0.5 + np.sin(2*ph)*0.3 + np.sin(3*ph)*0.2
    return bp(s, 250, 2500)*np.minimum(1, x/0.01)*np.minimum(1, (d-x)/0.03)

def tri(f, d, decay, vib=0.0):
    x = t(d); ph = 2*np.pi*np.cumsum(f*(1 + vib*np.sin(2*np.pi*9*x)))/SR
    s = 2*np.abs(2*((ph/(2*np.pi)) % 1) - 1) - 1
    return (s + np.sign(np.sin(ph))*0.25)*np.exp(-x*decay)*np.minimum(1, x/0.004)

# ───────── ARCADE: chunky chiptune ─────────
def arcade():
    o = canvas(0.45); place(o, sq(988, 0.08, 10), 0); place(o, sq(1319, 0.36, 7), 0.07)
    finish(bp(o, 150, 7000), "message_arcade", 0.6)
    finish(bp(sq(1568, 0.09, 30), 150, 7000), "incoming_arcade", 0.4)
    o = canvas(0.14)
    for i, f in enumerate([1047, 1319, 1568]): place(o, sq(f, 0.05, 25), i*0.03)
    finish(bp(o, 150, 7000), "sent_arcade", 0.4)
    # Sparkle: a quick down-up shimmer in a soft triangle-ish chip tone.
    o = canvas(0.6)
    for i, f in enumerate([1568, 1319, 1047, 1319, 1568, 2093]):
        place(o, tri(f, 0.25 if i == 5 else 0.06, 8 if i == 5 else 22), i*0.06)
    b, a = butter(2, 5500/(SR/2))
    finish(lfilter(b, a, o), "mention_arcade", 0.42)
    o = canvas(0.7)
    for i, f in enumerate([523, 659, 784, 1047]): place(o, sq(f, 0.3 if i == 3 else 0.09, 6 if i == 3 else 14), i*0.08)
    finish(bp(o, 150, 7000), "request_arcade", 0.55)

# ───────── BUBBLY: water drops and pops ─────────
def bubbly():
    o = canvas(0.38); place(o, drop(500, 1400), 0); place(o, drop(700, 1900), 0.12)
    finish(o, "message_bubbly")
    finish(drop(650, 1600, 0.1, 42), "incoming_bubbly", 0.5)
    x = t(0.12); f = 300 + 1500*(x/0.12)**1.5
    finish(np.sin(2*np.pi*np.cumsum(f)/SR)*np.exp(-x*20)*np.minimum(1, x/0.003), "sent_bubbly", 0.45)
    o = canvas(0.5)
    for i, (a, b) in enumerate([(450, 1200), (550, 1500), (700, 2000)]): place(o, drop(a, b), i*0.11)
    place(o, drop(900, 2600, 0.14, 25), 0.33)
    finish(o, "mention_bubbly", 0.75)
    o = canvas(0.6)
    for i, (a, b) in enumerate([(800, 2200), (650, 1700), (500, 1300), (750, 2100)]): place(o, drop(a, b, 0.12, 30), i*0.1)
    finish(o, "request_bubbly", 0.75)

# ───────── TOY BOX: squeaks, springs, kazoos ─────────
def toybox():
    o = canvas(0.42); place(o, squeak(0.12, 1300, 1750), 0); place(o, squeak(0.16, 1450, 2000), 0.17)
    finish(o, "message_toybox", 0.7)
    finish(squeak(0.1, 1400, 1850), "incoming_toybox", 0.45)
    x = t(0.2); f = 700 + 1500*(x/0.2)**0.8; ph = 2*np.pi*np.cumsum(f)/SR
    slide = (np.sin(ph) + bp(rng.standard_normal(len(x)), 1500, 5000)*0.08)*np.minimum(1, x/0.01)*np.minimum(1, (0.2-x)/0.03)
    finish(slide, "sent_toybox", 0.4)
    o = canvas(0.85); place(o, whistle(1000, 2300, 2200, 0.22, 0.85), 0); place(o, whistle(1000, 2100, 750, 0.45, 0.35), 0.32)
    finish(o, "mention_toybox", 0.65)
    o = canvas(0.55); place(o, honk(392, 0.14), 0); place(o, honk(392, 0.22), 0.2)
    finish(o, "request_toybox", 0.7)

# ───────── CRISP: clean snaps and soft tones ─────────
def crisp():
    o = canvas(0.32); place(o, snap(), 0); place(o, tok()*0.8, 0.1)
    finish(o, "message_crisp", 0.75)
    finish(tone(620, 980, 0.16, 22, 0.5, 0.15), "incoming_crisp", 0.55)
    x = t(0.13); s = np.sin(2*np.pi*np.cumsum(500 + 1400*(x/0.13)**1.6)/SR)*np.exp(-x*18)
    finish(s + bp(rng.standard_normal(len(x)), 1500, 6000)*np.exp(-x*35)*0.35, "sent_crisp", 0.5)
    o = canvas(0.45); place(o, snap(), 0); place(o, snap(0.9), 0.12); place(o, tok(2100, 0.16), 0.24)
    finish(o, "mention_crisp", 0.75)
    o = canvas(0.9); place(o, marimba(784), 0); place(o, marimba(988), 0.13); place(o, marimba(659, 0.6), 0.3)
    finish(o, "request_crisp")

for pack in (arcade, bubbly, toybox, crisp): pack()
