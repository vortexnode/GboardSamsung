# Upstream provenance

This repository is intended to be a downstream customization of:

- Repository: https://github.com/jasonwu1994/Gboard-patches
- Upstream default branch: main
- Commit inspected during project setup: 463269a0f315ef9cee749e10d155af1aedaa49ad
- Commit date: 2026-11-12
- License stated upstream: GNU General Public License v3.0
- Patch framework: Morphe

## Important distinction

Gboard Patches is a Morphe patch source. It does not contain Google's proprietary Gboard application source code. It builds patch bundles (`.mpp`) and metadata that Morphe applies to a user-provided compatible Gboard APK.

The term “GboardWU” commonly refers to a renamed/modified Gboard installation built using this patch ecosystem; it should not be mistaken for an independent open-source Gboard app source tree.

## Upstream synchronization

Before importing upstream code:
1. Confirm the current upstream commit and inspect its LICENSE and NOTICE.
2. Import the complete source tree, preserving file paths, upstream notices and attribution.
3. Record the exact imported commit in this file.
4. Keep downstream customizations in clearly identified commits.
5. Sync upstream changes deliberately; never overwrite downstream work blindly.

## Build requirements

The upstream README documents:
- Build patch bundle: `./gradlew :patches:buildAndroid`
- Regenerate patch metadata: `./gradlew generatePatchesList`
- Outputs: `patches/build/libs/*.mpp`, `patches-list.json`, `patches-bundle.json`
- Authentication to Morphe's GitHub Packages registry using `gpr.user` and `gpr.key` in Gradle properties or `GITHUB_ACTOR` and `GITHUB_TOKEN` environment variables.

Never commit credentials or tokens.
