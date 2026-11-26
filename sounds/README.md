# Bants sounds

Four styles for each of the five sound types, all synthesised from scratch by `make_sounds.py` (numpy + scipy), so they're ours to use. People pick them in the app under Profile → Sounds.

```
python3 make_sounds.py
for f in *_*.wav; do ffmpeg -y -i "$f" -c:a libvorbis -q:a 5 "../app/src/main/res/raw/sound_${f%.wav}.ogg"; done
```

| Type | Arcade | Bubbly | Toy box | Crisp |
|---|---|---|---|---|
| message | Coin bling (default) | Plip-plop drops | Two squeaks | Snap and tok |
| incoming | Chip blip | One drop | One squeak | Soft blip (default) |
| sent | Three-note jump | Rising bubble | Slide whistle | Fwip (default) |
| mention | Sparkle | Four rising drops | Wolf whistle (default) | Snap-snap-tok |
| request | Level-up arpeggio | Bubble cascade | Honk-honk | Marimba ding-dong (default) |
