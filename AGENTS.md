# Agent instructions

- This is a downstream Morphe patch project for the installed Gboard app, not a standalone keyboard and not Google's Gboard source code.
- Read README.md, docs/PROJECT_SCOPE.md and docs/UPSTREAM.md first.
- Do not touch upstream repositories or vortexnode/GbSu; this repository is the only write target.
- First perform the read-only audit required in docs/PROJECT_SCOPE.md. Do not start implementation until the audit and plan are reported.
- Preserve all upstream functionality and GPL-3.0 notices/attribution.
- Do not commit proprietary Gboard APKs, extracted proprietary assets, secrets, API keys or tokens.
- Do not invent support for fonts, Samsung layout details or Gboard versions; verify against source and tests.
- Spacebar gesture work must preserve tap-to-space and long-press cursor trackpad behavior.
- Build the .mpp patch bundle and run tests; do not claim an APK build because this project does not produce a standalone keyboard APK.
- Commit in small, reviewable changes with descriptive messages.
