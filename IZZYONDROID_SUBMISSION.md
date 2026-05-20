# IzzyOnDroid submission — LoloTrans

## App details

| Field | Value |
|---|---|
| App name | LoloTrans |
| Package ID | de.lolo.lolotrans |
| Author | immerzu (immerzu46@gmail.com) |
| License | Apache-2.0 |
| Source code | https://github.com/immerzu/LoloTrans |
| Releases | https://github.com/immerzu/LoloTrans/releases |
| Current version | 1.8 |
| Current version code | 9 |
| APK size | ~73 MB (release), ~77 MB (debug) |

## Summary

LoloTrans is an Android translator with a floating bubble. After the user actively taps the bubble, the app reads copied text from the clipboard, detects the source language and shows the translation in a movable overlay.

## Privacy

LoloTrans does not monitor the clipboard continuously. Clipboard text is read only after an explicit user action by tapping the floating bubble. Translation runs entirely on-device after initial model download. No trackers, analytics, or ads.

## IzzyOnDroid compliance check

| Criterion | Status | Note |
|---|---|---|
| F/LOSS license | ✅ | Apache 2.0 |
| Source accessible | ✅ | GitHub + GitLab |
| APK at GitHub releases | ✅ | Attached to release tag |
| No trackers/ads | ✅ | Zero tracking libraries |
| No self-updater | ✅ | — |
| Fastlane metadata | ✅ | In repo under fastlane/ |
| APK size < 30 MB | ⚠️ | ~73 MB — ML Kit native libraries are 66 MB |
| Proprietary components | ⚠️ | Google ML Kit (translate + language-id) |
| Target: end users | ✅ | — |

## Known concerns (to be disclosed)

1. **APK size**: ~73 MB (debug: ~77 MB). The bulk (~66 MB) is Google ML Kit native
   translation libraries (libtranslate_jni.so across 4 ABIs). ABI splits would reduce
   per-device size to ~45-50 MB but still exceed the 30 MB guideline.

2. **Google ML Kit**: Proprietary SDK for on-device translation and language
   identification. The app is functional without Google Play Services using the
   standalone ML Kit artifacts. Translation models are downloaded on first use.

## Submission

IzzyOnDroid issue tracker: https://codeberg.org/IzzyOnDroid/repo/issues

Template: "App Inclusion Request"
