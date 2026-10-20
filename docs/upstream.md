# Tracking HeliBoard upstream

Hebrish Keyboard is a GitHub fork of HeliBoard (`Hebrish-ASR/Hebrish-Keyboard`, parent
`HeliBorg/HeliBoard`; `Helium314/HeliBoard` redirects there). Created 2026-10-19.

## Remotes and branches

| Remote | URL |
| --- | --- |
| `origin` | `https://github.com/Hebrish-ASR/Hebrish-Keyboard.git` |
| `upstream` | `https://github.com/HeliBorg/HeliBoard.git` |

- `main` is the Hebrish product branch: the latest **stable** HeliBoard release tag plus our
  commits. It was reset to `v4.2` at fork time (upstream `main` was at `v4.3-beta1`), because we
  distribute the APK to end users and betas are not a base for that.
- Upstream's own branches are not mirrored; read them from the `upstream` remote.

## Releases and the automatic upstream sync

Set up 2026-10-20. Versions: `hebrish.properties` holds the Hebrish `revision`; an APK is
`<HeliBoard versionName>-hebrish.<revision>` with versionCode `HeliBoard versionCode * 100 +
revision` (HeliBoard 4.2 = 4201 -> `4.2-hebrish.1` = 420101). Upstream's own version lines are
never edited; the override is in the Hebrish block at the end of `app/build.gradle.kts`.

| Workflow | Trigger | Does |
| --- | --- | --- |
| `.github/workflows/hebrish-upstream-sync.yml` | Mondays 04:23 UTC, or manual | Newest stable HeliBoard tag (`vX.Y`, no alpha/beta) not in `main` → merge, `revision=1`, `scripts/check-seams.sh`, `assembleRelease`, push `main` + tag `vX.Y-hebrish.1`. Conflict, missing hook or failed build → nothing pushed, an issue opened (or commented on) instead |
| `.github/workflows/hebrish-release.yml` | Tag `v*-hebrish.*` pushed, or manual with a tag | Check hooks, check the tag equals the version in the sources, build signed release, GitHub release with `HebrishKeyboard-v<ver>.apk` |

A Hebrish-only release (engine bump, fix in `app/src/main/java/app/hebrish/`): bump
`revision` in `hebrish.properties`, commit, `git tag v4.2-hebrish.2 && git push origin main
v4.2-hebrish.2`.

Secrets on `Hebrish-ASR/Hebrish-Keyboard`: `RELEASE_KEYSTORE_B64`, `RELEASE_KEYSTORE_PASSWORD`,
`RELEASE_KEY_ALIAS` (the Hebrish app's release key, certificate SHA-256 `5CF6822350D0B2FF124021A0DF10BEC1C63A6E5E6159FA6029507134A1446994`, so both
apps share a signer and could later share the model through a signature permission), and
`SYNC_TOKEN` (a GitHub token with `repo` + `workflow` scope; `GITHUB_TOKEN` cannot push a merge
that touches `.github/workflows`, and tags it pushes do not start other workflows).

`scripts/check-seams.sh` is the guard against a merge that compiles but drops the integration:
it checks each hook below is present, that `onMicKey` is still inside `onEvent`'s
`KeyCode.VOICE_INPUT` branch, and that upstream's `switchToShortcutIme(this)` call has not come
back. Add a line there whenever a new seam is added to the table below.

Local builds: the full `assembleRelease`/`assembleDebug` (R8) gets the Gradle daemon killed on
daniellaptop when free RAM is ~2 GB (2026-10-20); `compileDebugNoMinifyKotlin
compileDebugNoMinifyJavaWithJavac --no-daemon -Dorg.gradle.jvmargs=-Xmx1536m` is enough to
check an engine bump compiles. Release builds happen in CI.

## Taking a new HeliBoard release by hand

Only needed when the sync workflow opened a conflict issue.

```bash
git fetch upstream --tags
git tag -l 'v*' --sort=-v:refname | grep -v -- -beta | head -1   # newest stable tag
git merge vX.Y                                                   # merge, never rebase: main is public
sed -i 's/^revision=.*/revision=1/' hebrish.properties && scripts/check-seams.sh
git commit -am "Merge HeliBoard vX.Y" && git tag vX.Y-hebrish.1 && git push origin main vX.Y-hebrish.1
```

Conflicts will come from the files listed below. Keep our side for the renamed values and
take theirs for everything else.

## Every place we diverge from upstream

Keep this list complete; it is what makes upstream merges cheap.

| File | Change | Why |
| --- | --- | --- |
| `app/build.gradle.kts` | `applicationId = "app.hebrish.keyboard"`; APK named `HebrishKeyboard_<ver>-<type>.apk` | Installs next to stock HeliBoard |
| `app/src/{main,debug,debugNoMinify}/res/values/clip_provider.xml`, `gesture_data.xml` | Provider authorities `app.hebrish.keyboard[.debug].{clipprovider,provider}` | Authorities must be unique per device; HeliBoard's would make install fail with `INSTALL_FAILED_CONFLICTING_PROVIDER` while HeliBoard is installed |
| `app/src/main/res/values/donottranslate.xml`, `app/src/{debug,debugNoMinify}/res/values/strings.xml` | `english_ime_name` = "Hebrish Keyboard" (debug: "Hebrish Keyboard debug") | App and keyboard name |
| `app/src/main/res/values/strings.xml` | "HeliBoard Settings" / "Spell Checker" / "Spell Checker Settings" → "Hebrish Keyboard …" | English UI only; translated `values-*/strings.xml` still say HeliBoard (not changed, to keep merges trivial) |
| `README.md` | Hebrish header above the upstream README | Credit and scope |
| `docs/upstream.md` | This file | |
| `app/src/main/java/helium314/keyboard/latin/LatinIME.java` | Three lines marked `// Hebrish`: `onEvent` calls `HebrishVoice.onMicKey` instead of `mRichImm.switchToShortcutIme`; `setInputView` calls `HebrishVoice.attach`; `onFinishInputViewInternal` calls `HebrishVoice.onFinishInput` | Dictation happens inside the keyboard; no switch to a voice IME |
| `app/src/main/java/helium314/keyboard/latin/RichInputMethodManager.kt` | `isShortcutImeReady` is always `true` | Upstream hides/disables the mic key when no voice IME is installed; ours always works (it opens voice setup if the model or mic permission is missing) |
| `app/src/main/AndroidManifest.xml` | `RECORD_AUDIO` and `INTERNET` permissions; `app.hebrish.voice.VoiceSetupActivity` with its own launcher entry ("Hebrish voice") | Upstream HeliBoard has **no internet permission**; we need it once, to download the model (~660 MB) from Hugging Face, plus catalogue/update checks |
| `app/build.gradle.kts` (end of file) | Hebrish block: version override from `hebrish.properties`, release signing from `RELEASE_KEYSTORE*` env | Appended after upstream's blocks so merges don't touch it |
| `app/build.gradle.kts` | `abiFilters` arm64-v8a only; sherpa-onnx AAR fetch task + dependency; model catalogue copied from `hebrish/shared/models.json` into assets; `kotlin.srcDir("../hebrish/android/core/src/main/java")` | The engine. arm64 only because sherpa-onnx adds ~32 MB per ABI |
| `app/proguard-rules.pro` | Keep `com.k2fsa.sherpa.onnx.**` | Its JNI layer uses the Kotlin classes by name; R8 would strip them in release builds |
| `.gitignore` | `app/libs/*.aar` | The downloaded sherpa-onnx AAR |
| New files (no upstream counterpart) | `app/src/main/java/app/hebrish/voice/` (`HebrishVoice.kt`, `VoiceSetupActivity.kt`), `app/src/main/res/values/hebrish_voice.xml`, `.gitmodules` + `hebrish/` submodule, `hebrish.properties`, `scripts/check-seams.sh`, `.github/workflows/hebrish-*.yml` | Never conflict on merge. Upstream's own workflows are left as they are |

The Kotlin package (`helium314.keyboard.*`, `namespace`) is deliberately unchanged: renaming it
would touch every source file and make every upstream merge conflict.

## The Hebrish engine (submodule)

`hebrish/` is a git submodule of `Hebrish-ASR/Hebrish-App` (the voice-keyboard and
desktop apps). The keyboard compiles its `android/core/src/main/java` (package
`app.hebrish.core`: catalogue, model download/store, recorder, transcriber) as part of the app;
see that folder's README for why it's a source folder rather than a Gradle module (AGP 8.9 vs
HeliBoard's AGP 9.1 with built-in Kotlin, which needs `kotlin.srcDir`, not `java.srcDir`).

```bash
git clone --recurse-submodules https://github.com/Hebrish-ASR/Hebrish-Keyboard.git
# existing clone:
git submodule update --init
# take engine changes:
git -C hebrish pull origin main && git add hebrish && git commit -m "Engine: bump hebrish to <sha>"
```

Build needs JDK 17 (`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleDebug` on
daniellaptop, whose shell `JAVA_HOME` points at a missing JDK) and the Android NDK version in
`app/build.gradle.kts` (HeliBoard's own JNI dictionary code).

## How voice input works

`app.hebrish.voice.HebrishVoice`: mic key → if the mic permission or the model is missing, open
`VoiceSetupActivity`; else start `Recorder` (16 kHz, max 90 s) and load the model in the
background. Mic key again → `Transcriber.transcribe` on a worker thread → `LatinIME.onTextInput`
(HeliBoard's own text path, so its composing/undo state stays consistent), with a leading space
unless the cursor follows whitespace. A pill at the top of the keyboard shows the state; tapping
it discards the take. Closing the field drops an open take, and a transcript that finishes after
the field closed is discarded rather than typed into the next one.

Model files live in app-private storage (`ModelPrefs` file `prefs`; HeliBoard's own prefs are
`<package>_preferences`, so no clash). The keyboard does not share a model with the Hebrish
voice-keyboard app; each downloads its own.

Status (2026-10-20): released through CI from `v4.2-hebrish.1`; **not yet run on a device**. Problems are debugged in public through this repo's issues.

## Rules

- Never open issues or PRs on HeliBoard from an agent. HeliBoard's `AI_USAGE.md` prohibits
  AI-generated issues and PRs; anything worth sending upstream is written and sent by a human.
- `gh` in this clone defaults to `Hebrish-ASR/Hebrish-Keyboard` (`gh repo set-default`), so
  `gh pr create` targets the fork, not HeliBoard. Check this before creating any PR.
- GPL-3.0: every APK we distribute must have its exact source published here (tag each release).
