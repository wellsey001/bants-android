# Bants sounds

All five sounds are synthesised from scratch by `make_sounds.py` (numpy + scipy), so they're ours to use. Run it, then convert to OGG:

```
python3 make_sounds.py
for f in *.wav; do ffmpeg -y -i "$f" -c:a libvorbis -q:a 5 "${f%.wav}.ogg"; done
```

| File | When it plays |
|---|---|
| `bants_message` | New message (notification sound, and in-app on other screens) |
| `bants_incoming` | Message arrives in the chat you're looking at |
| `bants_sent` | Your message reaches the server |
| `bants_mention` | Someone @mentions you (a cheeky wolf whistle) |
| `bants_request` | Someone adds you |
