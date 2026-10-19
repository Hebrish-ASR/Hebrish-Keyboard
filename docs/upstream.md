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

## Taking a new HeliBoard release

```bash
git fetch upstream --tags
git tag -l 'v*' --sort=-v:refname | grep -v -- -beta | head -1   # newest stable tag
git merge vX.Y                                                   # merge, never rebase: main is public
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

The Kotlin package (`helium314.keyboard.*`, `namespace`) is deliberately unchanged: renaming it
would touch every source file and make every upstream merge conflict.

## Rules

- Never open issues or PRs on HeliBoard from an agent. HeliBoard's `AI_USAGE.md` prohibits
  AI-generated issues and PRs; anything worth sending upstream is written and sent by a human.
- `gh` in this clone defaults to `Hebrish-ASR/Hebrish-Keyboard` (`gh repo set-default`), so
  `gh pr create` targets the fork, not HeliBoard. Check this before creating any PR.
- GPL-3.0: every APK we distribute must have its exact source published here (tag each release).
