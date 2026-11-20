# Agent instructions

- This is **GboardSamsung**, a downstream Morphe patch project maintained by **Vortex Node** for the installed Gboard app (derived from Jason Wu's `jasonwu1994/Gboard-patches`). It is not a standalone keyboard app and does not contain Google's proprietary Gboard source code.
- Read `README.md`, `docs/PROJECT_SCOPE.md`, and `docs/UPSTREAM.md` before making architectural changes.
- Do not touch upstream repositories (`jasonwu1994/Gboard-patches`) or `vortexnode/GbSu`; this repository is the only write target.
- Preserve all kept GboardSamsung features, GPL-3.0 notices (`LICENSE`, `NOTICE`), and Jason Wu upstream attribution (`UPSTREAM_COMMIT.txt`, `UPSTREAM_README.md`).
- Keep technical package namespaces (`dev.jason.gboardpatches`, `dev.jason.com.google.android.inputmethod.latin`, `extensions/gboard-patches.rve`) intact unless a deliberate compatibility migration is requested.
- Do not commit proprietary Gboard APKs, extracted proprietary assets, secrets, API keys, or tokens.
- Do not invent support for fonts, Samsung layout details, or Gboard versions; verify against source, bindings, and tests.
- Spacebar gesture work (`samsung_spacebar_gestures`) must preserve tap-to-space, bidirectional language switching, and long-press cursor control, and remain independent of `swipe_down_to_dismiss_keyboard`.
- Build outputs are Morphe `.mpp` patch bundles and metadata (`patches-list.json`, `patches-bundle.json`), not a standalone keyboard APK. Do not claim Gradle/Morphe builds or tests passed unless they actually executed in the current environment.
- Commit in small, reviewable changes with descriptive messages.
