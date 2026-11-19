# GboardSamsung

A downstream customization project based on [Gboard Patches](https://github.com/jasonwu1994/Gboard-patches), targeting a Samsung Keyboard-style typing experience while retaining Gboard's own input engine.

> **Important:** This repository contains Morphe patches for the installed Gboard app. It is not a standalone keyboard app and does not contain Google's proprietary Gboard source code.

## Upstream

- **Upstream project:** https://github.com/jasonwu1994/Gboard-patches
- **Upstream branch:** `main`
- **Upstream commit inspected for initial project planning:** `463269a0f315ef9cee749e10d155af1aedaa49ad` (2026-11-12)
- **Patch framework:** Morphe
- **License:** GPL-3.0; preserve upstream LICENSE, NOTICE, attribution and source-distribution obligations when importing code.

**Repository state:** This is currently a documentation seed, not yet a complete buildable copy of upstream. Import/synchronize the complete upstream source tree before implementation.

## Project goals

1. Preserve Gboard as the actual keyboard and its native typing, correction, spelling, prediction, dictionaries, voice typing, language support and other built-in capabilities.
2. Add bidirectional language switching by swiping across the spacebar, following Samsung Keyboard's interaction.
3. Adapt Gboard's visual presentation toward Samsung Keyboard / One UI:
   - themes and colors;
   - key shapes, dimensions and spacing;
   - typography and fonts where technically supported;
   - primary and secondary/long-press symbols;
   - number row, bottom row and toolbar layout.
4. Keep upstream Gboard Patches features working.
5. Evaluate integration of the Rambler implementation from https://github.com/vortexnode/GbSu as a separate compatible feature. Do not confuse the user's Gemini-powered Rambler in GbSu with any Gboard-native Rambler feature or similarly named upstream patch.

## Critical constraints

- Do not replace Gboard with AOSP LatinIME or a new standalone IME.
- Do not claim this repository contains Google's Gboard source code. It contains patches applied to a user-provided Gboard APK through Morphe.
- Do not remove or regress Gboard's native language models, correction, spelling, prediction, voice typing, dictionaries, emoji, GIF, stickers, clipboard or other capabilities.
- Do not break long-press-spacebar cursor trackpad mode while implementing spacebar swipe. Gesture arbitration must be designed and tested.
- Do not assume fonts or all Samsung-specific visual details are controllable through the current theme importer. Audit the target Gboard version and theme format first.
- Do not bundle or redistribute Google's proprietary Gboard APK.
- Preserve upstream attribution, notices and GPL-3.0 obligations.

## Device target

Samsung Galaxy S22 5G (SM-S901U1), Android 16 / One UI 8.

## Build model

This project builds Morphe patch bundles (`.mpp`) and patch metadata, not a standalone Gboard APK. The user applies the bundle to a compatible Gboard APK through Morphe. See upstream build instructions and authentication requirements.

## Start here

Read [docs/PROJECT_SCOPE.md](docs/PROJECT_SCOPE.md) and [docs/UPSTREAM.md](docs/UPSTREAM.md) before changing code.
