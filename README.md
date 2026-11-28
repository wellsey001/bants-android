<img src="docs/icon.png" width="96" alt="Bants logo">

# Bants for Android

A WhatsApp-style messaging app with Discord-style servers, for your own [Bants server](https://github.com/wellsey001/Bants). There's also a [Windows app](https://github.com/wellsey001/bants-desktop), and a web version your server hosts for everyone else (including iPhones).

**Download:** [Bants.apk (latest)](https://github.com/wellsey001/bants-android/releases/latest/download/Bants.apk)

The app checks for new versions itself and offers to install them.

## What it does

**Chats**
- Sign up and log in (with an invite code if your server needs one)
- Add people by **username** (they accept a request) or **friend code** (instant)
- 1:1 chats and groups, with group info: members, admins, rename, group picture
- Photos (resized before upload, or sent in original quality), videos, files and hold-to-record voice notes
- Swipe a message to reply; long-press to react, or send a ✨ super reaction
- GIF search, your own stickers, and up to 50 custom emojis (static or animated)
- Polls, locations, forwarding and message info
- Pin, archive and mute chats; search all your messages
- @mentions in groups (they reach you even if you've muted the chat)
- Link previews, disappearing messages (24 hours, 7 days or 90 days), edit and delete for everyone
- Live delivery, typing indicators, online and last seen, and ✓ sent / ✓✓ delivered / blue ✓✓ read ticks
- Chats are saved on the phone, so they open instantly and work offline. Messages you send offline go out when you're back online.

**Servers**
- Switch between **Chats** and **Servers** with one button
- Text, announcement, forum, voice and stage channels, categories and threads
- Roles and permissions, with role colours and role icons
- Invites, your own server link that never runs out, and invite backgrounds
- Rules screening, events, polls, webhooks, server emojis and stickers
- Moderation: kick, ban, timeout, slowmode, pins and an audit log
- Animated (GIF) server icons and banners

**Calls and voice**
- Voice and video calls, 1:1 and group, with a full-screen incoming call screen even when Bants is closed
- Voice channels with a Discord-style video grid that works in portrait
- Screen sharing, picture-in-picture, and camera and video quality settings
- Camera backgrounds: blur or your own picture
- Soundboard (favourite sounds work in any server), join and leave sounds, per-channel voice quality
- Stage channels: moderators speak, everyone else listens and can raise a hand

**Profiles**
- Avatars and banners (GIFs move), bio and pronouns
- Profile themes, avatar decorations, profile effects and name styles

**On your phone**
- App themes: Bants, Midnight, Ocean, Forest, Sunset, Candy or Mono, plus light and dark mode
- App icons: Classic, Lime, Sky, Berry, Candy or Shadow (Settings). If the home screen shortcut disappears after changing it, add Bants back from the app drawer.
- Push notifications, even when the app is closed (see below)
- Mute chats, leave groups, block people

The default server is `https://bants.kanji-avalanche.xyz`. You can change it on the login screen.

## Building
Every push to `main` builds the APK with GitHub Actions and publishes it as a release. The APK is signed with the key in `app/debug.keystore`, so each new build installs over the last one.

To build a branch without releasing it, run the **build** workflow on that branch (`gh workflow run build.yml --ref <branch>`).

## Push notifications
Pushes come through Firebase, so messages arrive even when the app is closed. They switch on when `app/google-services.json` (from your Firebase project) is in the repo and the server has `FCM_SERVICE_ACCOUNT` set. Without them the app still works, but notifications only arrive while it's running.
