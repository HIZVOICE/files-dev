# Changelog — Files Dev

Package `com.hyperfiles.manager` · minSdk 24 · compile/target SDK 36 · Kotlin + XML Views + Material 3.
All releases are debug-signed, ABI-split APKs (arm64-v8a, armeabi-v7a).

## [2.9] — 2026-07-19 (versionCode 20)
### Added
- **payload.bin support** for custom ROMs: `PayloadDumper` parses the A/B OTA CrAU format; `PayloadActivity` lists partitions and extracts full-OTA images (REPLACE / REPLACE_XZ / REPLACE_BZ / ZERO), marking incremental payloads as unsupported.
- **"New folder"** action in the file options menu (works on every browsing surface).
### Changed
- Launcher icon recolored from blue to an **emerald→teal gradient**.

## [2.8 and earlier] — Foundation
- All-codec video & audio playback via **libVLC**; background playback + media notifications through foreground bound services; resume-position memory.
- Video player: gesture seek / brightness / volume, playback-speed control, aspect cycling, next/prev.
- **Archives**: zip / 7z / tar / gz / bz2 / xz (Apache Commons Compress + XZ); extract & compress.
- **App installs**: apk plus split formats (xapk / apks / apkm) via the PackageInstaller session API.
- **Viewers**: PDF (PdfRenderer), Office/ODF text extraction, HTML (WebView), text, hex, image.
- **Dev tools**: terminal, system info, root file ops (chmod / chown / mount / symlink), browse `/`, `/system`, `/vendor`, `/data`.
- Recent files, duplicate finder, cache cleaner, **recycle bin**, **secure folder** (BiometricPrompt + pattern), hidden-file toggle.
- Android 16 (API 36) Notification `ProgressStyle` / Live Updates (Samsung Now Bar); AMOLED theming; first-launch permission prompt; bounce/press animations.
