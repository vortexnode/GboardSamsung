# Upstream provenance

**GboardSamsung** (`https://github.com/vortexnode/GboardSamsung`, maintained by **Vortex Node**) is a downstream customization of:

- **Upstream repository:** https://github.com/jasonwu1994/Gboard-patches
- **Upstream author:** Jason Wu
- **Upstream branch:** `main`
- **Imported upstream commit:** `463269a0f315ef9cee749e10d155af1aedaa49ad` (recorded in `UPSTREAM_COMMIT.txt`)
- **Upstream commit date:** `2026-11-12`
- **License:** GNU General Public License v3.0 (`LICENSE`, `NOTICE`)
- **Patch framework:** Morphe

## Important distinction

Both upstream Gboard Patches and downstream GboardSamsung are Morphe patch sources. Neither contains Google's proprietary Gboard application source code. They build patch bundles (`.mpp`) and catalog metadata that Morphe applies to a user-provided compatible Gboard APK (`18.5.4.993608717-release-arm64-v8a`).

Upstream previously used `GboardWu` as the default Package Rename display name; GboardSamsung uses `GboardSamsung` as its default display name while preserving technical namespaces (`dev.jason.gboardpatches`, `dev.jason.com.google.android.inputmethod.latin`, `extensions/gboard-patches.rve`) for binary/runtime compatibility.

## Downstream divergence summary

1. **Added:** `Samsung Spacebar Gestures` (`samsung_spacebar_gestures`) as an independent public patch.
2. **Removed (12 upstream patches):** `latin_globe_key_ignore_interval`, `web_clipboard`, `zhuyin_bottom_row_key_sizes`, `zhuyin_quick_traditional_simplified_toggle`, `zhuyin_slide_input`, `g_logo_on_spacebar`, `english_qwerty_up_flick_uppercase`, `lan_ftp_server`, `swipeable_custom_top_row`, `enable_ocr_scan_text`, `enable_accessibility_layout`, and `enable_cursor_trackpad_mode`, along with their exclusive dependencies (Apache FTPServer/MINA, QuickJS native payload, G Logo image payload).
3. **Preserved upstream external resources (intentional):**
   - Custom Theme preview archive download URL in `GboardCustomThemePreviewRepository.java` (pinned by SHA-256 to upstream release `custom-theme-previews-v1`) until a downstream mirror is hosted.
   - Frosted Glass FAQ issue references in `GboardFrostedGlassSettingsFeature.java` pointing to the original upstream troubleshooting threads.
   - `.github/workflows/import-upstream.yml` for controlled manual upstream inspection/import.

## Upstream synchronization guidelines

When pulling future fixes from upstream:
1. Inspect upstream commits and verify `LICENSE` / `NOTICE` compatibility.
2. Cherry-pick or merge selectively — do **not** re-introduce the 12 removed features or overwrite downstream branding (`Vortex Node`, `vortexnode/GboardSamsung`, `GboardSamsung` default display name) or `samsung_spacebar_gestures`.
3. Update `UPSTREAM_COMMIT.txt` when advancing the tracked upstream baseline.
4. Keep `gboard-port-product-catalog.json`, `gboard-port-product-catalog.sha256`, `gboard-version-bindings.json`, and `patches-list.json` synchronized.

## Build requirements

- Build patch bundle: `./gradlew :patches:buildAndroid`
- Regenerate patch metadata: `./gradlew generatePatchesList`
- Outputs: `patches/build/libs/*.mpp`, `patches-list.json`, `patches-bundle.json`
- Authentication to Morphe's GitHub Packages registry uses `gpr.user` and `gpr.key` in Gradle properties or `GITHUB_ACTOR` and `GITHUB_TOKEN` environment variables.

Never commit credentials, tokens, or proprietary Gboard APKs.
