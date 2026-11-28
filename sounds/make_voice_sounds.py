"""Join and leave chimes for voice channels and calls (like Discord's): two soft bell notes,
going up when someone joins and down when someone leaves.
Run: python3 make_voice_sounds.py   (writes voice_join.wav and voice_leave.wav)"""
import numpy as np
from scipy.io import wavfile

SR = 44100

def bell(f, d=0.55):
    x = np.arange(int(SR * d)) / SR
    # A warm, rounded note: the fundamental, a soft octave and a little shimmer that fades fast.
    s = np.sin(2 * np.pi * f * x) + 0.28 * np.sin(2 * np.pi * 2 * f * x) * np.exp(-9 * x) \
        + 0.12 * np.sin(2 * np.pi * 3.01 * f * x) * np.exp(-16 * x)
    return s * np.exp(-5.5 * x) * np.minimum(1, x / 0.004)

def chime(notes, gap=0.11, name='x'):
    out = np.zeros(int(SR * (gap * len(notes) + 0.6)))
    for i, f in enumerate(notes):
        b = bell(f) * (1 if i == len(notes) - 1 else 0.85)
        at = int(i * gap * SR)
        out[at:at + len(b)] += b[:len(out) - at]
    out = out / np.max(np.abs(out)) * 0.7
    fade = int(0.02 * SR)
    out[-fade:] *= np.linspace(1, 0, fade)
    wavfile.write(f'{name}.wav', SR, (out * 32767).astype(np.int16))

# E5 → B5 (up) to join, B5 → E5 (down) to leave.
chime([659.25, 987.77], name='voice_join')
chime([987.77, 659.25], name='voice_leave')
