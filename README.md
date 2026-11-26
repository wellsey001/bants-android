<img src="docs/icon.png" width="96" alt="Bants logo">

# Bants for Android

A WhatsApp-style messaging app for your own [Bants server](https://github.com/wellsey001/Bants).

**Download:** [Bants.apk (latest)](https://github.com/wellsey001/bants-android/releases/latest/download/Bants.apk)

## What it does
- Sign up and log in (with an invite code if your server needs one)
- Add people by **username** (they accept a request) or **friend code** (instant)
- 1:1 chats and groups
- Photos, with automatic resizing before upload
- Live delivery, typing indicators, online and last seen
- Ticks: ✓ sent, ✓✓ delivered, blue ✓✓ read
- Edit (for 15 minutes) and delete for everyone
- Mute chats, leave groups, block people
- Notifications while the app is running in the background
- Light and dark mode

The default server is `https://bants.kanji-avalanche.xyz`. You can change it on the login screen.

## Building
Every push to `main` builds the APK with GitHub Actions and publishes it as a release. The APK is signed with the key in `app/debug.keystore`, so each new build installs over the last one.

## Coming next
- Push notifications through Firebase, so messages arrive even when the app is fully closed
- Voice notes, videos and files
- Replies and reactions
- Group info screen
