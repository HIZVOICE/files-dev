# Changelog — Files Dev

Package `com.hyperfiles.manager` · minSdk 24 · compile/target SDK 36 · Kotlin + XML Views + Material 3.
All releases are debug-signed, ABI-split APKs (arm64-v8a, armeabi-v7a).

## [5.1] — 2026-07-20 (versionCode 42)
### Changed
- **Recycle bin, System info, and APK info rebuilt in Jetpack Compose + Material 3** — continuing the screen-by-screen migration begun with Settings in 5.0. Recycle bin is now a Compose `LazyColumn` with a per-item dropdown (Restore · Open · Delete forever) and an Empty-bin confirm dialog; System info and APK info are Compose scaffolds (monospaced detail, copy / install actions, APK icon rendered via Compose `Image`).
- **`FilesDevTheme` now honors the app's theme mode** (System / Light / Dark / AMOLED) and matches the existing palette (accent `#3482FF`), so the Compose screens look identical to the rest of the app instead of defaulting to the Material baseline.
- **Recycle-bin index moved off SharedPreferences into Room** (new `trash` table + `TrashDao`). `TrashBin`'s public API is unchanged, so callers were untouched; the DB uses destructive migration for the index only — trashed files on disk are unaffected.
### Removed
- Old XML `TrashAdapter` / RecyclerView for the recycle bin (superseded by the Compose list).

## [5.0] — 2026-07-20 (versionCode 41)
### Changed
- **Began the Jetpack Compose + Material 3 + Room modernization** (hybrid, migrating screen by screen). Toolchain added: Jetpack Compose (BOM 2024.06.00, compiler 1.5.14 for Kotlin 1.9.24) and Room 2.6.1 via KSP; Gradle heap raised to accommodate the Compose compiler.
- **Settings screen rebuilt in Jetpack Compose** — the first migrated screen. Everything else remains on XML Views for now.
### Added
- **Room database foundation** (AppDatabase + Favorites table/DAO) to underpin the persistence migration off SharedPreferences.

## [4.9] — 2026-07-20 (versionCode 40)
### Removed
- **Quick access row** removed from the Storage tab.
- **Pull-to-refresh removed from the Storage tab** (it was triggering on swipe); the internal list refreshes on resume / navigation instead.
- **Storage analyzer removed** (screen + button).
### Changed
- Recent tab now shows a **display-only circular storage-usage ring** (percent used, with used / total) — no navigation or other function.

## [4.8] — 2026-07-20 (versionCode 39)
### Fixed
- **Android/data sizes & empty folders.** The elevated `ls -lA` parser is now robust across device formats (toybox ISO date vs. coreutils month name), so files show their **real size**. Restricted folders no longer display a bogus **"0 items"** — they show "Folder" and open normally.
- **Storage analyzer empty bars.** It now computes real segments — **System & other, Apps (installed APK sizes), Videos, Audio, Photos, Documents** — and draws **filled comparative bars** (each row shows size + % of used).
### Added
- **Quick access** shortcut row on the Storage tab — chips for Downloads, Camera, Pictures, Screenshots, Documents, Movies, Music (whichever exist); tap to open.

## [4.7] — 2026-07-20 (versionCode 38)
### Added
- **Bottom tab bar auto-hides on scroll** — it slides away when scrolling down and reappears when scrolling up (Storage & Recent); the bar now overlays the list so hiding it reveals more content.
- **Springy overscroll bounce** at the top/bottom of lists (Storage, Recent, Category, Browse) using a physics spring.
- **Pull-to-refresh on the internal-storage (Storage) list** — swipe down at the top to re-scan and reload folders.

## [4.6] — 2026-07-20 (versionCode 37)
### Changed
- **Video player UI** rebuilt to the Files-by-Google layout: a **full-width seek bar** with times at the ends (+ a rotate/orientation button), and a **centered bottom transport row** — rewind 10s · previous · play/pause · next · forward 10s. Mute and Share moved into the top bar. The on-screen 10-second skip buttons are back (double-tap L/R still seeks too).
### Added
- **Multi-select on the Storage and Recent lists.** Long-press to select; a contextual header bar shows the count with **Share · Delete · Copy · Select-all** (Back / ✕ exits).
- **Storage analyzer** (Recent tab): device **used / total / free** plus a size **breakdown by type** (Images, Videos, Audio, Documents, Archives, Apps, and Other & system) with per-type bars.

## [4.5] — 2026-07-20 (versionCode 36)
### Changed
- **Share is now a first, always-visible button** on the multi-select top bar (Category and Browse screens), instead of sometimes collapsing into the ⋮ overflow. Order is Share · Delete · Copy, with the rest in overflow.

## [4.4] — 2026-07-20 (versionCode 35)
### Fixed
- **Secure folder opened without asking for a password** from the Storage-tab shortcut — it launched the folder directly, bypassing the lock. It now goes through the lock screen (PIN / pattern / fingerprint), same as the Settings entry.
### Changed
- Video player controls **auto-hide faster** (2s instead of ~3.6s), with the existing fade-out.
### Added
- **Share** action in the photo viewer toolbar (shares the current image). The video player already exposes Share in its top bar.

## [4.3] — 2026-07-20 (versionCode 34)
### Changed
- **Recent tab** now has a **Recycle bin** shortcut alongside Cache and Duplicates.
- **Storage tab** gained a **Secure folder** (lock) button in the top header (shown on the Storage tab only).
- **Video player controls** moved down onto the seek-bar row so they no longer cover the video: `prev · play/pause · [rounded seek] · next` in a single bottom row (the seek bar is a thicker rounded pill between play/pause and next). The center overlay pill was removed.

## [4.2] — 2026-07-20 (versionCode 33)
### Fixed
- **Status-bar overlap on tall / newer devices (e.g. Pixel 6 Pro).** Under targetSdk 36 edge-to-edge is force-enabled (the opt-out flag is ignored), so headers/toolbars drew under the status bar and the top menu couldn't be tapped. Every screen now pads its content by the system-bar insets (and the video player insets its overlaid top/bottom control bars); the fullscreen video surface itself stays edge-to-edge. When the platform isn't edge-to-edge the insets are 0, so there's no double padding.
- **Permission on Android 10 and older.** `hasAllFilesAccess()` always returned true on pre-Android-11 devices, so the app never requested `READ_EXTERNAL_STORAGE` — it couldn't list files and the category shortcuts came up empty. It now checks/requests the real runtime permission on API ≤29.

## [4.1] — 2026-07-20 (versionCode 32)
### Added
- **Swipe down to dismiss** the photo, video and audio players. The photo viewer follows the finger and fades the background (release past a threshold to close, otherwise it snaps back); the audio player closes on a downward swipe over the album art; the video player closes on a fast, near-vertical downward flick (tuned to stay distinct from the brightness/volume drags).

## [4.0] — 2026-07-20 (versionCode 31)
### Added
- **Swipe to change track / photo.** A fast horizontal flick now switches items: **swipe left = next, swipe right = previous** in the video player, the audio player (swipe across the album art), and the photo viewer. The photo viewer pages through sibling images in the folder with a slide animation and an "n / total" counter. In the video player the flick is tuned to be distinct from the horizontal drag-to-seek gesture and suppresses the seek when a flick is detected.

## [3.9] — 2026-07-20 (versionCode 30)
### Added
- **Multi-select in the media/category screen.** Long-press (or the new "Select" menu action) now enters selection mode instead of opening the options menu; a selection toolbar shows the count plus Share, Copy, Move, Delete, Compress (ZIP/7z), Checksum, and Select all. Tapping toggles items.
- **Select by date.** New "Select by date" action selects all items in the current view from **Today / Yesterday / Last 7 days / Last 30 days** at once (e.g. all of yesterday's photos).
### Notes
- Share is available both in the per-file options menu and the multi-select toolbar.

## [3.8] — 2026-07-20 (versionCode 29)
### Added
- **File operations inside Android/data.** Copy, Move (paste), Delete, Rename and New folder now run through the elevated shell (Shizuku or root) whenever the source, destination, or folder is a restricted path — previously only browse/open worked there. `Elevated` gained `delete`/`rename`/`mkdir`/`copyInto`/`moveInto` and a `needed()` check; slow ops run off the UI thread. Deletes of restricted items are permanent (the app recycle bin can't reach `Android/data`).

## [3.7] — 2026-07-20 (versionCode 28)
### Fixed
- **Android/data via Shizuku:** opening a file (e.g. a video) failed with "couldn't read file" and every entry showed **0 MB**. The elevated copy-out now targets a shell-writable, app-readable temp dir on shared storage (`/sdcard/FilesDevTmp`) instead of the app's private cache — the Shizuku shell uid (2000) can't write into `/data/data/<pkg>` (only root could). Sizes are now parsed from `ls -lA` (the app can't `stat` restricted entries, so `File.length()` returned 0).

## [3.6] — 2026-07-20 (versionCode 27)
### Added
- **Shizuku access for Android/data.** New Dev-tab "Android/data" tile pings the Shizuku service, requests permission, and then browses the OS-restricted `Android/data` / `Android/obb` trees (blocked by scoped storage on Android 11+) via an ADB-privileged shell.
- New `Elevated` shell backend that transparently picks **Shizuku** (when granted) or **root** (`su`): restricted/unreadable folders now list through it instead of bouncing to the SAF picker, and restricted files are copied out via the elevated shell so viewers can open them. `ShizukuProvider` + `moe.shizuku.manager.permission.API_V23` added; `dev.rikka.shizuku:api`/`provider` dependencies.

## [3.5] — 2026-07-20 (versionCode 26)
### Added
- **HyperOS "Liquid Engine" spring press.** Rewrote `PressScale` on top of `androidx.dynamicanimation` `SpringAnimation`: shrinks to 0.94x on touch-down (stiff, near-critical) and springs back to 1.0x with an underdamped **0.45 damping-ratio overshoot** on release.
  - Interruptible (`animateToFinalPosition` retargets in-flight and preserves velocity).
  - Click-safe (touch listener never consumes events; clicks, long-press and ripple still fire).
  - Applied app-wide (file/folder rows, category tiles, dev tiles, cards) via the unchanged `attach()` API; attaches once per recycled view.

## [3.4] — 2026-07-20 (versionCode 25)
### Changed
- **Playlist → Material bottom sheet** (24dp top corners, `surfaceContainerLow`, grab handle, swipe-to-dismiss; no Close button). Current track highlighted.
- **Audio art container** redesigned: 248dp / 28dp-rounded card, drop shadow + soft radial glow, embedded album art, subtle gradient placeholder when art is missing.
- **Seek bar → Material 3 `Slider`** (8dp softened track, halo thumb); time counters regrouped directly beneath the track.
- **Video overlay**: prev/play/next wrapped in a translucent glass pill; on-screen rewind/forward buttons removed (10s skip is double-tap on the left/right half).

## [3.3] — 2026-07-20 (versionCode 24)
### Added
- **MediaStyle media notifications** backed by a `MediaSession` for both audio and video: title, album art, prev/play-pause/next, and an interactive **seek-bar scrubber** (Android 10+) with elapsed/total times. Lock-screen controls; system controls drive play/pause/seek/next/prev via session callbacks. (`androidx.media`)

## [3.2] — 2026-07-19 (versionCode 23)
### Changed
- **Media rows + thumbnails everywhere** — Audio, Documents, Archives and Apps categories now use the rich media row.
### Added
- Best-effort **embedded album-art thumbnails** for audio (cached, background loader, recycling-safe).
- Centered file-type thumbnails for non-media files.

## [3.1] — 2026-07-19 (versionCode 22)
### Added
- **Files-by-Google-style category list**: rounded thumbnails with play badge, `size • date` subtitles.
- **Folder-source chips** (All + each source folder) and a **grid/list toggle** + search in the category toolbar.
- **"Feature settings" bottom sheet** from the filter icon: Layout (List/Grid), Sort by (Name/Size/Time/Type), Sort order (Forward/Reverse), Display (hidden files).
### Changed
- Video player transport reordered to rewind · prev · play · next · forward; added a **Share** button.

## [3.0] — 2026-07-19 (versionCode 21)
### Changed
- **HyperOS "Storage" home redesign**: top-right search/filter/overflow icon row + large per-tab title; 2×2 colored category tiles (Docs/Images/Videos/Music) with a Type/More expander; "Internal storage" header with a working new-folder button; folder rows restyled (larger icon, bold name, `date | N items`, chevron).
- Renamed the Home tab to **Storage** (Recent and Devs retained).

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
