"""Regenerates every Bants icon from icon.py + android_icon.py.
Usage: python3 make_icons.py <bants-android dir> <bants-server dir> <brand kit dir>"""
import os, subprocess, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from icon import mark, Y, P
from android_icon import foreground_xml, mono_xml, silhouette_svg, SCALE, TX, TY, TILT, SILHOUETTE

AND, SRV, BRAND = sys.argv[1:4]
RES = f"{AND}/app/src/main/res"
CX, CY = 107.5, 113  # visual centre of the mark (art units)

def svg(inner, size=200):
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 200 200">{inner}</svg>'

def centred(s):
    return f'<g transform="translate({100 - CX*s} {100 - CY*s}) scale({s})">{mark()}</g>'

SQUARE = svg(f'<rect width="200" height="200" rx="44" fill="{Y}"/>' + centred(0.80))
FLAT   = svg(f'<rect width="200" height="200" fill="{Y}"/>' + centred(0.80))
ROUND  = svg(f'<circle cx="100" cy="100" r="100" fill="{Y}"/>' + centred(0.72))
MARK   = f'<svg xmlns="http://www.w3.org/2000/svg" width="135" height="164" viewBox="40 31 135 164">{mark()}</svg>'

def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "w").write(text)

def png(svg_text, path, w, h=None):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    tmp = "/tmp/_icon.svg"; open(tmp, "w").write(svg_text)
    args = ["rsvg-convert", tmp, "-w", str(w)] + (["-h", str(h)] if h else []) + ["-o", path]
    subprocess.run(args, check=True)

def android(res):
    write(f"{res}/drawable/ic_launcher_foreground.xml", foreground_xml())
    write(f"{res}/drawable/ic_launcher_monochrome.xml",
          mono_xml(108, 108, SCALE, TX, TY, "Bants themed-icon layer: the B with its face cut out."))
    # notification icon: mark fills a 24dp box (art bbox ~135x164 -> scale to 24 units with a little padding)
    s = 22 / 164
    write(f"{res}/drawable/ic_notification.xml",
          mono_xml(24, 24, round(s, 4), 12 - CX*s, 12 - CY*s, "Bants status-bar icon (white silhouette, Android tints it)."))
    # tight-cropped logo for in-app use (login screen)
    write(f"{res}/drawable/bants_logo.xml", foreground_xml()
          .replace('android:width="108dp"', 'android:width="135dp"').replace('android:height="108dp"', 'android:height="164dp"')
          .replace('android:viewportWidth="108"', 'android:viewportWidth="135"').replace('android:viewportHeight="108"', 'android:viewportHeight="164"')
          .replace(f'android:scaleX="{SCALE}" android:scaleY="{SCALE}" android:translateX="{TX:.2f}" android:translateY="{TY:.2f}"',
                   'android:translateX="-40" android:translateY="-31"')
          .replace("launcher icon foreground", "in-app logo"))
    for d, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        png(SQUARE, f"{res}/mipmap-{d}/ic_launcher.png", px)
        png(ROUND, f"{res}/mipmap-{d}/ic_launcher_round.png", px)

android(RES)
png(SQUARE, f"{AND}/docs/icon.png", 512)

# server: Unraid template icon + README
write(f"{SRV}/assets/icon.svg", SQUARE)
png(SQUARE, f"{SRV}/assets/icon.png", 512)

# brand kit
L = f"{BRAND}/logo"
write(f"{L}/bants-icon.svg", SQUARE); write(f"{L}/icon.svg", SQUARE)
write(f"{L}/bants-icon-square.svg", FLAT)
write(f"{L}/bants-icon-round.svg", ROUND)
write(f"{L}/bants-mark.svg", MARK)
write(f"{L}/bants-silhouette.svg", svg(silhouette_svg("#111111")))
for px in (48, 192, 512, 1024): png(SQUARE, f"{L}/bants-icon-{px}.png", px)
png(SQUARE, f"{L}/icon.png", 512)
png(MARK, f"{L}/bants-mark-512.png", 422, 512)
android(f"{BRAND}/android/res")
print("done")
