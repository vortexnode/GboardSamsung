# Project scope and implementation brief

## Product definition

GboardSamsung is a downstream Morphe patch project. It customizes the real installed Gboard app while preserving Gboard's input engine. It is not a new Android InputMethodService and is not a fork of Google's proprietary Gboard application source.

## Required outcome

- Retain Gboard's own typing engine and built-in capabilities: autocorrection, spelling/grammar support where available, next-word prediction, suggestions, dictionaries/language models, multilingual typing, voice typing, emoji, GIFs, stickers, clipboard and other native features.
- Samsung Keyboard-like appearance and interaction: themes, typography/fonts where technically possible, symbols and alternate/long-press symbols, key geometry, number row, bottom row and toolbar.
- Bidirectional language switching by swiping on the spacebar, similar to Samsung Keyboard.
- Preserve long-press spacebar cursor movement / trackpad behavior.
- Evaluate integration of the user's Rambler AI implementation from https://github.com/vortexnode/GbSu without replacing native Gboard voice typing or other Google features.

## Existing upstream capabilities verified from upstream README and source tree

Upstream: https://github.com/jasonwu1994/Gboard-patches

The upstream README documents:
- Custom Theme: import custom ZIP themes and themes from the Rboard repository.
- Frosted Glass: live background blur and custom-theme transparency on supported Android 12+ devices.
- Custom Symbols: dedicated symbols tab and quick access from comma long-press popup.
- Gboard feature unlocks including Grammar Checker, Inline Suggestions / Smart Compose, AI Writing Tools, Advanced Voice Typing, OCR / Scan Text, cursor trackpad mode, split keyboard, accessibility layout, emoji size, key shape selection and more.
- Dictionaries & Predictions: choose resource versions for dictionaries, predictions and input correction.
- Package Rename: install patched Gboard alongside official Gboard.
- Other features include clipboard enhancements, toolbar editing buttons, swipeable custom top row, calculator and backup/restore.

The upstream source tree contains implementations and tests for custom themes, custom symbols, language models, cursor trackpad, spacebar logo and a Rambler-related Gboard selector patch. The upstream README does **not** establish that every font or Samsung layout detail is already supported.

## Gaps requiring investigation

1. **Spacebar language swipe:** no verified general Latin-layout left/right spacebar language-switch patch was identified in the upstream README/tree inventory. Design a new patch only after inspecting Gboard's actual gesture dispatch and existing globe/trackpad paths.
2. **Gesture conflict:** preserve long-press-spacebar cursor trackpad mode. Define touch slop, horizontal threshold, velocity/time policy, cancellation rules and precedence between tap, long-press, cursor movement and horizontal swipe. Avoid intercepting ordinary space insertion.
3. **Samsung-like visual design:** custom themes already exist upstream, but exact Samsung geometry and layout may require additional resource/bytecode patches. Audit target Gboard version and theme schema.
4. **Fonts:** do not promise arbitrary font replacement until the theme format and Gboard font/resource paths are verified. Document exactly which typography properties can be changed.
5. **Secondary symbols:** inspect existing Gboard custom-symbol patch and target language layouts. Distinguish adding a custom symbols panel from changing per-key long-press alternate characters.
6. **Rambler:** inspect the GbSu implementation and its dependencies, permissions, API keys/configuration, Firebase setup, lifecycle and UI. The upstream Gboard Patches tree has a Rambler-related Gboard selector patch; do not assume it is the same as the user's Gemini-powered Rambler AI feature in GbSu. Propose a safe integration architecture before porting anything.

## Non-negotiable engineering rules

- Start with an audit; no broad refactor or speculative patch.
- Work only in this downstream repository. Never push to upstream jasonwu1994/Gboard-patches or the user's GbSu repository.
- Keep upstream changes isolated and documented; retain attribution, LICENSE and NOTICE.
- Add tests for gesture state/threshold logic and patch contracts.
- Validate against an explicitly supported Gboard version. Do not claim compatibility with arbitrary versions.
- Preserve all upstream features by default. Report unavoidable incompatibilities before implementation.
- Do not download, commit or redistribute Google's proprietary Gboard APK, extracted proprietary assets or private user data.
- Do not put credentials, API keys, signing keys or tokens in the repository.
- Do not claim a successful build unless CI actually built the .mpp bundle and tests passed.

## Target device

Samsung Galaxy S22 5G, model SM-S901U1, Android 16, One UI 8.

## Acceptance criteria

- The upstream patch project builds successfully in CI.
- Existing upstream tests pass.
- Existing Gboard native input behavior remains intact on a supported Gboard build.
- Spacebar tap inserts a space.
- Horizontal swipe left/right changes between enabled languages consistently.
- Long-press spacebar still enters cursor trackpad mode and remains usable.
- Custom theme support remains functional.
- Samsung-inspired theme/layout work does not break symbols, alternate characters, suggestions or toolbar.
- Any font customization is demonstrated on-device or clearly marked unsupported.
- Rambler integration is separately tested and does not replace native Gboard voice typing.
- README documents supported Gboard versions, Morphe application steps, limitations and rollback.

## Required first task for the coding assistant

Perform a read-only audit of the complete upstream source and the GbSu Rambler implementation. Report:
1. Exact upstream commit and current target Gboard version(s).
2. Build prerequisites and whether GitHub Packages authentication is required.
3. Existing patch modules relevant to themes, fonts, symbols, alternate characters, language switching, cursor trackpad and Rambler.
4. A concrete design for spacebar gesture arbitration.
5. Feasibility and risks of importing GbSu Rambler.
6. A staged implementation plan and test matrix for the Samsung Galaxy S22.

Do not implement the spacebar gesture or visual redesign until this audit is reviewed.
