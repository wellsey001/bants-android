# Bants icon: smirking speech-bubble B as a die-cut sticker on yellow.
P='#5B4BFF'; INK='#111111'; Y='#FFD23F'; W='#FFFFFF'; PINK='#FF4D8D'
TOP = "M68 44 H118 A25 25 0 0 1 118 94 H68 Q62 94 62 88 V50 Q62 44 68 44 Z"
BOT = "M68 100 H126 A28 28 0 0 1 126 156 H86 Q72 167 44 173 Q58 161 62 146 V106 Q62 100 68 100 Z"
TILT = -6

def shapes(fill, stroke=None, width=0, dx=0, dy=0):
    st = f' stroke="{stroke}" stroke-width="{width}" stroke-linejoin="round"' if stroke else ''
    return f'<g transform="translate({dx} {dy})" fill="{fill}"{st}><path d="{TOP}"/><path d="{BOT}"/></g>'

def face():
    return (
        # brows: one flat and unimpressed, one hitched right up
        f'<path d="M81 58.5 L97 60.5" stroke="{W}" stroke-width="5" stroke-linecap="round"/>'
        f'<path d="M107 57 Q116 46.5 127 53" stroke="{W}" stroke-width="5" fill="none" stroke-linecap="round"/>'
        # half-lidded eyes glancing sideways
        f'<path d="M82.5 70 A7.5 7.5 0 0 0 97.5 70 Z" fill="{W}"/>'
        f'<path d="M107.5 70 A7.5 7.5 0 0 0 122.5 70 Z" fill="{W}"/>'
        f'<circle cx="93.2" cy="73" r="3.3" fill="{INK}"/><circle cx="118.2" cy="73" r="3.3" fill="{INK}"/>'
        f'<path d="M81.5 70 H98.5 M106.5 70 H123.5" stroke="{INK}" stroke-width="3" stroke-linecap="round"/>'
        # lopsided smirk with a dimple, and the tongue poking out
        f'<path d="M104.5 130.2 C104 140.5 108 145.5 112 145.5 C116.5 145.5 120 140 118.6 127.4 Z" fill="{PINK}" stroke="{INK}" stroke-width="1.6" stroke-linejoin="round"/>'
        f'<path d="M111.6 132.5 Q112.2 136.5 111.8 140" stroke="#C2185B" stroke-width="1.8" fill="none" stroke-linecap="round"/>'
        f'<path d="M90 129 Q111 136.5 130 119" stroke="{W}" stroke-width="6.5" fill="none" stroke-linecap="round"/>'
        f'<path d="M127.5 113.5 Q133.5 117 131.5 124" stroke="{W}" stroke-width="3.5" fill="none" stroke-linecap="round"/>'
    )

def mark(shadow=6):
    return (
        f'<g transform="rotate({TILT} 100 100)">'
        + shapes(INK, INK, 22, shadow, shadow)  # hard drop shadow
        + shapes(INK, INK, 22)                  # outer outline
        + shapes(W, W, 14)                       # die-cut white border
        + shapes(P)                              # the B
        + face()
        + '</g>'
    )

def full(bg=Y):
    return f'<rect width="200" height="200" fill="{bg}"/>' + mark()

def silhouette(color):
    """One-colour version (themed icons / notifications): B with the face cut out."""
    return (f'<g transform="rotate({TILT} 100 100)"><mask id="m"><rect width="200" height="200" fill="#fff"/>'
            + face().replace(W, '#000').replace(INK, '#000').replace(PINK, '#000') +
            f'</mask><g mask="url(#m)">{shapes(color, color, 14)}</g></g>')
