# Files Dev

A power-user Android file manager for the AOSP / custom-ROM community — an all-in-one blend of a HyperOS-style file browser, an all-codec media player, an archive manager, and ROM-developer tooling.

`com.hyperfiles.manager` · Kotlin + XML Views + Material 3 · minSdk 24 · compile/target SDK 36

## Features

- **All-codec playback** (libVLC) for video and audio, with background playback, resume, gesture controls, and MediaStyle notifications (lock-screen scrubber on Android 10+).
- **HyperOS-inspired UI**: Storage home with colored category tiles, Files-by-Google-style media lists (thumbnails, album art, folder chips), a Material bottom-sheet "Feature settings" panel, and a physics-based "Liquid Engine" spring press.
- **Archives**: zip, 7z, tar, gz, bz2, xz — extract and compress.
- **App installs**: apk + split formats (xapk / apks / apkm) via PackageInstaller.
- **Viewers**: PDF, Office/ODF (text), HTML, text, hex, image.
- **ROM / dev tools**: `payload.bin` (CrAU) partition extractor, terminal, system info, root file ops (chmod/chown/mount/symlink), browse `/system` `/vendor` `/data`.
- **Utilities**: recent files, duplicate finder, cache cleaner, recycle bin, secure folder (biometric + pattern).

See [CHANGELOG.md](CHANGELOG.md) for the full history.

## Build

```bash
# JDK 17, Android SDK (platforms;android-36, build-tools;36.0.0)
gradle assembleDebug
```

Outputs ABI-split debug APKs (`arm64-v8a`, `armeabi-v7a`) under `app/build/outputs/apk/debug/`.

## Tech

libVLC · Apache Commons Compress + XZ · AndroidX Media (MediaSession) · dynamicanimation · Biometric · Glide · Material Components.

> Debug-signed builds for development/testing.
