# GboardSamsung

**[English](README.md) | [Русский](README.ru.md)**

**GboardSamsung** is a downstream Morphe patch project maintained by **Vortex Node**, derived from Jason Wu's [Gboard Patches](https://github.com/jasonwu1994/Gboard-patches) (`GPL-3.0`). It focuses on bringing a Samsung Keyboard-style typing experience (including bidirectional spacebar language switching while preserving tap-to-space and long-press cursor control) to the real Gboard app while retaining Gboard's own typing engine, predictions, dictionaries, voice typing, clipboard, and core capabilities.

> **Important:** This repository contains Morphe patches for a user-provided Gboard APK. It is **not** a standalone keyboard app and does **not** contain Google's proprietary Gboard source code or APK binaries.

## Supported target

- **Target Gboard build:** `18.5.4.993608717-release-arm64-v8a` (`18.5.4`)
- **Primary test device:** Samsung Galaxy S22 5G (`SM-S901U1`), Android 16 / One UI 8

## Installation (Morphe Manager)

GboardSamsung is distributed as Morphe patches rather than a pre-built APK. You patch your own copy of Gboard directly on your phone using **Morphe Manager**:

### 1. Add the patch source
- **One-tap:** [Add GboardSamsung to Morphe Manager](https://morphe.software/add-source?github=vortexnode/GboardSamsung)
- **Manual:** Open Morphe Manager → **Patch sources → + → Remote**, then paste:
  ```text
  https://github.com/vortexnode/GboardSamsung
  ```

### 2. Get the supported Gboard APK
- Download a clean standalone **arm64-v8a** APK of Gboard **`18.5.4.993608717-release-arm64-v8a`** (e.g., from APKMirror — choose the full APK, not a split `.apkm` bundle unless your manager unsplit-converts it automatically).

### 3. Patch & install
1. In Morphe Manager, select the downloaded Gboard `18.5.4` APK.
2. Review and select the patches you want (by default, `Package Rename` sets the package ID to `gboardsamsung.com.google.android.inputmethod.latin` and the display name to `GboardSamsung`, so you can install it alongside stock Gboard without conflicts).
3. Run the patcher and install the resulting APK.

### 4. Enable the keyboard
- **Samsung (One UI):** *Settings → General management → Keyboard list and default* → enable **GboardSamsung** and set it as default.
- **Other Android devices:** *Settings → System → Keyboard → On-screen keyboard* → enable **GboardSamsung**.

> **Updates:** Once the remote source is added, Morphe Manager automatically checks this repository for new patch bundle releases.

## Upstream attribution & provenance

- **Original author (upstream):** Jason Wu (`jasonwu1994`) — [jasonwu1994/Gboard-patches](https://github.com/jasonwu1994/Gboard-patches)
- **Modified and adapted by (downstream):** `vortexnode` (Vortex Node) — [vortexnode/GboardSamsung](https://github.com/vortexnode/GboardSamsung)
- **Imported upstream baseline:** `463269a0f315ef9cee749e10d155af1aedaa49ad` (`2026-11-12`, recorded in `UPSTREAM_COMMIT.txt`; original upstream README archived in `UPSTREAM_README.md`)
- **Patch framework:** Morphe (`app.morphe.patches`)
- **License:** GNU General Public License v3.0 (`LICENSE`, `NOTICE`)

## Active patch catalog (34 patches)

GboardSamsung includes **34** public Morphe patches (33 curated upstream features plus the downstream `Samsung Spacebar Gestures` patch):

- **Downstream Samsung experience (1):**
  - `Samsung Spacebar Gestures` — swipe left/right on the spacebar to switch languages in a unified cycle while preserving short tap for space and ~300 ms hold + drag for cursor movement (independent of `Swipe Down to Dismiss Keyboard`).
- **Themes, appearance & layout (12):**
  - `Custom Theme`, `Frosted Glass`, `Rounded Keyboard Panel`, `Change emoji size`, `Top Toolbar Item Count`, `Access Points menu style`, `Enable split keyboard`, `Key Shape Selection`, `Custom Symbols`, `Emojis, stickers & GIFs Tab Order`, `Settings Homepage Override`, `Close Proactive Suggestions`.
- **Typing, editing & keyboard tools (10):**
  - `Long-Press Editing Shortcuts`, `Toolbar Editing Buttons`, `Quick Insert`, `Swipe Down to Dismiss Keyboard`, `Keypress Vibration Intensity Control`, `Floating Web Search`, `Simple Calculator`, `Incognito Mode Toggle`, `Clipboard Enhancements`, `Clipboard Custom Character Limit`.
- **Language, AI, suggestions & voice (7):**
  - `AI Writing Tools`, `Advanced Voice Typing`, `Grammar Checker`, `Inline Suggestions`, `Enable Inline Autofill Suggestions`, `Use Bluetooth Microphone`, `Dictionaries & Predictions`.
- **Packaging, security & utilities (4):**
  - `Package Rename` (default package ID: `gboardsamsung.com.google.android.inputmethod.latin`, default app & spell checker display name: `GboardSamsung`, also supports official `com.google.android.inputmethod.latin` and custom package IDs), `Add Gboard Signature Bypass`, `Backup & Restore`, `Developer options`.

### Upstream features removed in GboardSamsung (12)

To keep the downstream codebase focused and maintainable, 12 upstream features and their exclusive dependencies/payloads (Apache FTPServer/MINA, QuickJS native runtime, Zhuyin-specific overrides, G Logo image payload, etc.) were removed:

1. `Latin Globe Key Ignore Interval`
2. `Web Clipboard` (standard Gboard Clipboard, `Custom Clipboard Enhancements`, and `Clipboard Custom Character Limit` are preserved)
3. `Zhuyin Bottom Row Key Sizes`
4. `Zhuyin Quick Traditional/Simplified Toggle`
5. `Zhuyin Slide Input` (`Show custom symbols` is preserved)
6. `G Logo on Spacebar` (`Samsung Spacebar Gestures` is preserved)
7. `English QWERTY Up-Flick Uppercase`
8. `FTP Server`
9. `Swipeable Custom Top Row`
10. `Enable OCR / Scan Text`
11. `Enable accessibility layout`
12. `Enable cursor trackpad mode` (stock/Samsung spacebar cursor control via `Samsung Spacebar Gestures` is preserved)

## Build model

This project builds Morphe patch bundles (`.mpp`) and patch catalog metadata (`patches-list.json`, `patches-bundle.json`), not a standalone keyboard APK:

- Build patch bundle: `./gradlew :patches:buildAndroid`
- Regenerate patch metadata: `./gradlew generatePatchesList`
- Run unit tests: `./gradlew test`
- Morphe GitHub Packages authentication requires `gpr.user` / `gpr.key` (or `GITHUB_ACTOR` / `GITHUB_TOKEN`). Never commit credentials, tokens, or proprietary Gboard APKs.

Successful changes merged into `main` are tested first; conventional `fix:`, `feat:`, `bump:`, `perf:`, and `build(morphe-source):` commits trigger a versioned Morphe release containing the `.mpp` bundle and refreshed source metadata. Test-only and documentation-only commits do not publish a new bundle.

## Documentation

- [docs/PROJECT_SCOPE.md](docs/PROJECT_SCOPE.md) — product scope, engineering rules, and acceptance criteria
- [docs/UPSTREAM.md](docs/UPSTREAM.md) — upstream provenance and synchronization guidelines
- [docs/REPOSITORY_CLEANUP_REPORT.md](docs/REPOSITORY_CLEANUP_REPORT.md) — downstream cleanup inventory, catalog sync, and verification report
- [UPSTREAM_README.md](UPSTREAM_README.md) — original upstream Gboard Patches documentation
