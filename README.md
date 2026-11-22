# GboardSamsung

<p align="center">
  <strong>Samsung-inspired customization for the original Gboard</strong><br>
  A community-maintained Morphe patch source by Vortex Node, based on Jason Wu's Gboard Patches.
</p>

<p align="center">
  <a href="README.md">English</a> · <a href="README.ru.md">Русский</a>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-blue.svg" alt="GPL-3.0"></a>
  <img src="https://img.shields.io/badge/patches-34-informational" alt="34 patches">
  <img src="https://img.shields.io/badge/target-Gboard%2018.5.4-success" alt="Gboard 18.5.4">
  <img src="https://img.shields.io/badge/platform-Android-orange" alt="Android">
</p>

GboardSamsung aims to bring selected Samsung Keyboard-style interactions and customization to the **real Gboard app**, while keeping Gboard's own typing engine and core features.

> [!IMPORTANT]
> This repository provides **Morphe patches**, not a standalone keyboard app. It does not include Google's proprietary Gboard source code or APK. You need to provide a compatible Gboard APK and patch it yourself with Morphe Manager.

## Install

The easiest way to use GboardSamsung is to add this repository as a remote patch source in **Morphe Manager**.

### 1. Add the patch source

<p align="center">
  <a href="https://morphe.software/add-source?github=vortexnode/GboardSamsung"><strong>＋ Add GboardSamsung to Morphe Manager</strong></a>
</p>

Or add it manually:

1. Open **Morphe Manager**.
2. Go to **Patch sources → + → Remote**.
3. Enter the repository address:

   `https://github.com/vortexnode/GboardSamsung`

### 2. Prepare a compatible Gboard APK

Use a clean, standalone **arm64-v8a** APK matching this target:

`18.5.4.993608717-release-arm64-v8a`

For example, obtain the full APK from a trusted APK archive. Avoid split `.apkm` packages unless your patching workflow can correctly convert or install them.

### 3. Select patches and build

1. Open the downloaded Gboard APK in Morphe Manager.
2. Select the GboardSamsung patches you want.
3. Review the **Package Rename** options. The default package ID is `gboardsamsung.com.google.android.inputmethod.latin`, and the default app name is `GboardSamsung`.
4. Run the patch process and install the resulting APK.

The default package ID allows GboardSamsung to be installed alongside the official Gboard package. The official package ID and custom package IDs are also supported.

### 4. Enable the keyboard

- **Samsung One UI:** Settings → General management → Keyboard list and default. Enable **GboardSamsung**, then choose it as the default keyboard.
- **Other Android devices:** Settings → System → Keyboard → On-screen keyboard. Enable **GboardSamsung** and select it as needed.

### Source updates

After the remote source has been added, Morphe Manager can check the repository for updated patch bundles and make newer versions available when they are published. Update availability depends on the manager's source-checking behavior; it is not necessarily instantaneous.

## Compatibility

| Item | Supported target |
|---|---|
| Gboard | `18.5.4.993608717-release-arm64-v8a` |
| Architecture | `arm64-v8a` |
| Primary test device | Samsung Galaxy S22 5G (`SM-S901U1`) |
| Android / One UI | Android 16 / One UI 8 |

Compatibility with other Gboard versions or device configurations is not guaranteed unless separately verified.

## Patch catalog

GboardSamsung currently contains **34 active Morphe patches**: 33 selected upstream patches and one downstream patch maintained for the Samsung-style spacebar experience.

### Samsung-style interaction

- **Samsung Spacebar Gestures** — swipe left or right on the spacebar to switch enabled languages; short tap still inserts a space, and long-press plus drag moves the cursor.

### Themes, appearance & layout · 12

- **Custom Theme** — import custom ZIP themes, including themes from the Rboard repository.
- **Frosted Glass** — add live background blur and adjust custom-theme opacity.
- **Rounded Keyboard Panel** — choose which panel corners are rounded and configure their radii.
- **Change emoji size** — adjust emoji size.
- **Top Toolbar Item Count** — customize the number of toolbar items.
- **Access Points menu style** — switch between new and legacy Access Points menu styles.
- **Enable split keyboard** — enable the split keyboard layout.
- **Key Shape Selection** — expose key-shape options in Theme details.
- **Custom Symbols** — add a dedicated custom-symbols tab and a comma long-press shortcut.
- **Emojis, stickers & GIFs Tab Order** — reorder the bottom tabs in the media panel.
- **Settings Homepage Override** — switch between the new and legacy Gboard settings homepages.
- **Close Proactive Suggestions** — add a dismiss button to the proactive suggestions bar.

### Typing, editing & keyboard tools · 10

- **Long-Press Editing Shortcuts** — add text-editing actions such as Select all, Undo, Copy, Cut, Paste and Redo.
- **Toolbar Editing Buttons** — add Select All, Copy, Cut and Paste to the toolbar menu.
- **Quick Insert** — enable the Quick Insert panel and its toolbar entry.
- **Swipe Down to Dismiss Keyboard** — dismiss the keyboard with a downward swipe from the suggestions or toolbar.
- **Keypress Vibration Intensity Control** — adjust vibration intensity.
- **Floating Web Search** — open a floating web page for quick searches.
- **Simple Calculator** — show an expression and its result in the suggestion row.
- **Incognito Mode Toggle** — add an Incognito toggle and configure clipboard and voice-typing availability in that mode.
- **Clipboard Enhancements** — customize clipboard retention, item limits, previews, labels, ordering and columns.
- **Clipboard Custom Character Limit** — set the maximum character count for each text clipboard item.

### Language, AI, suggestions & voice · 7

- **AI Writing Tools** — enable AI writing tools for supported languages.
- **Advanced Voice Typing** — enable Advanced Voice Typing and related punctuation options.
- **Grammar Checker** — enable the grammar-checking setting.
- **Inline Suggestions** — enable Smart Compose.
- **Enable Inline Autofill Suggestions** — enable inline autofill suggestions.
- **Use Bluetooth Microphone** — expose Bluetooth microphone support for voice typing.
- **Dictionaries & Predictions** — choose resource versions for dictionaries, predictions and input correction.

### Packaging, security & utilities · 4

- **Package Rename** — configure the Android package ID and app display name. Defaults: `gboardsamsung.com.google.android.inputmethod.latin` and `GboardSamsung`.
- **Add Gboard Signature Bypass** — bypass Gboard signature-whitelist checks.
- **Backup & Restore** — export or restore patch settings and back up, compare or restore Gboard PB/XML flag data.
- **Developer options** — enable Gboard Developer options and the Flag Editor.

### Upstream patches not included

Twelve upstream features were removed from this downstream project to keep its scope and dependencies focused:

<details>
<summary>Show the 12 excluded upstream patches</summary>

1. Latin Globe Key Ignore Interval
2. Web Clipboard
3. Zhuyin Bottom Row Key Sizes
4. Zhuyin Quick Traditional/Simplified Toggle
5. Zhuyin Slide Input
6. G Logo on Spacebar
7. English QWERTY Up-Flick Uppercase
8. FTP Server
9. Swipeable Custom Top Row
10. Enable OCR / Scan Text
11. Enable accessibility layout
12. Enable cursor trackpad mode

Removing these patches did not remove shared functionality that GboardSamsung still uses. For example, the standard Gboard clipboard and the downstream Samsung Spacebar Gestures patch are retained.

</details>

## FAQ

<details>
<summary><strong>Is GboardSamsung a separate keyboard app?</strong></summary>

No. It is a set of Morphe patches applied to a compatible Gboard APK. The resulting patched Gboard app can use a separate package ID so it can coexist with the official installation.
</details>

<details>
<summary><strong>Do I need to install Morphe Manager?</strong></summary>

Yes. Morphe Manager is used to add the patch source, select patches, patch your Gboard APK and install the result.
</details>

<details>
<summary><strong>Will every Gboard version work?</strong></summary>

No such guarantee is made. The currently documented target is Gboard `18.5.4.993608717-release-arm64-v8a`.
</details>

<details>
<summary><strong>Does adding the source update the keyboard automatically?</strong></summary>

No. Morphe Manager can check for new patch bundles from the remote source. You still need to select and apply the patches to a compatible Gboard APK when you want to update your patched app.
</details>

## Build & verification

This repository builds **Morphe patch bundles (`.mpp`)** and patch-catalog metadata. It does not build a standalone keyboard APK.

Common developer commands:

```bash
./gradlew :patches:buildAndroid
./gradlew generatePatchesList
./gradlew test
```

The CI workflow runs module unit tests, verifies that `patches-list.json` is up to date, and builds the Morphe Android patch bundle. The publish workflow analyzes commits and publishes a Morphe source release after a successful CI run on `main`.

Release classification is controlled by [`.releaserc`](.releaserc). The current rules include breaking changes as major, `feat:` as minor, and `fix:`, `bump:`, `perf:`, `build(morphe-source):` and `docs:` as patch releases. Documentation commits are hidden from generated release-note sections, but **the current configuration still classifies `docs:` commits as patch releases**.

Morphe GitHub Packages authentication requires `gpr.user` / `gpr.key` or the corresponding GitHub Actions credentials. Never commit credentials, signing keys, tokens or proprietary Gboard APKs.

## Project provenance

- **Upstream author:** Jason Wu (`jasonwu1994`) — [Gboard Patches](https://github.com/jasonwu1994/Gboard-patches)
- **Downstream maintainer:** Vortex Node (`vortexnode`) — [GboardSamsung](https://github.com/vortexnode/GboardSamsung)
- **Imported upstream baseline:** `463269a0f315ef9cee749e10d155af1aedaa49ad` (recorded in [`UPSTREAM_COMMIT.txt`](UPSTREAM_COMMIT.txt))
- **Patch framework:** [Morphe](https://github.com/MorpheApp)
- **License:** [GNU GPL v3.0](LICENSE); see also [`NOTICE`](NOTICE)

The upstream repository is credited as the historical source of the selected patches. GboardSamsung is maintained independently and does not require an active upstream import workflow.

## Documentation

- [Project scope](docs/PROJECT_SCOPE.md) — goals, boundaries and acceptance criteria.
- [Upstream provenance](docs/UPSTREAM.md) — origin, divergence and synchronization policy.
- [Repository cleanup report](docs/REPOSITORY_CLEANUP_REPORT.md) — removed components and catalog verification.
- [Archived upstream README](UPSTREAM_README.md) — original upstream project documentation.
