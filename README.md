<img src="docs/icon.png" width="96" alt="Bants logo">

# Bants for Android

A WhatsApp-style messaging app for your own [Bants server](https://github.com/wellsey001/Bants).

**Download:** [Bants.apk (latest)](https://github.com/wellsey001/bants-android/releases/latest/download/Bants.apk)

## What it does
- Sign up and log in (with an invite code if your server needs one)
- Add people by **username** (they accept a request) or **friend code** (instant)
- 1:1 chats and groups
- Photos (resized before upload), videos, files and hold-to-record voice notes
- Swipe a message to reply; long-press to react with an emoji
- Group info: members, admins, add or remove people, rename, group picture
- Voice and video calls (1:1), with a full-screen incoming call screen even when Bants is closed
- GIF search and your own stickers (make them from photos, or save ones friends send)
- Pin, archive and mute chats; search all your messages
- @mentions in groups (they reach you even if you've muted the chat)
- Link previews and tappable links
- Disappearing messages (24 hours, 7 days or 90 days)
- Chats are saved on the phone, so they open instantly and work offline; messages you send offline go out when you're back online
- Live delivery, typing indicators, online and last seen
- Ticks: ✓ sent, ✓✓ delivered, blue ✓✓ read
- Edit (for 15 minutes) and delete for everyone
- Mute chats, leave groups, block people
- Notifications while the app is running in the background
- Light and dark mode

The default server is `https://bants.kanji-avalanche.xyz`. You can change it on the login screen.

## Building
Every push to `main` builds the APK with GitHub Actions and publishes it as a release. The APK is signed with the key in `app/debug.keystore`, so each new build installs over the last one.

## Push notifications
Pushes come through Firebase, so messages arrive even when the app is closed. They switch on when `app/google-services.json` (from your Firebase project) is in the repo and the server has `FCM_SERVICE_ACCOUNT` set. Without them the app still works, but notifications only arrive while it's running.
