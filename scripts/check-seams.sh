#!/usr/bin/env bash
# Fails when a Hebrish hook into HeliBoard is missing. A HeliBoard merge can succeed and
# compile while upstream has moved the code we hook, leaving a keyboard whose mic key no
# longer reaches Hebrish; compilation alone does not catch that. Every seam in
# docs/upstream.md that carries behaviour is checked here. Run from anywhere.
set -uo pipefail
cd "$(dirname "$0")/.."
fail=0
need() { # file, fixed string
  grep -qF -- "$2" "$1" 2>/dev/null || { echo "MISSING in $1: $2"; fail=1; }
}
L=app/src/main/java/helium314/keyboard/latin/LatinIME.java
R=app/src/main/java/helium314/keyboard/latin/RichInputMethodManager.kt
M=app/src/main/AndroidManifest.xml

need "$L" 'HebrishVoice.INSTANCE.onMicKey(this)'
need "$L" 'HebrishVoice.INSTANCE.attach(view)'
need "$L" 'HebrishVoice.INSTANCE.onFinishInput()'
need "$R" 'val isShortcutImeReady get() = true'
need "$M" 'android.permission.RECORD_AUDIO'
need "$M" 'android.permission.INTERNET'
need "$M" 'app.hebrish.voice.VoiceSetupActivity'

# The mic hook must sit in the VOICE_INPUT branch of onEvent, where upstream handles the key.
grep -A3 'KeyCode.VOICE_INPUT == event.getKeyCode()' "$L" | grep -q 'HebrishVoice' ||
  { echo "MOVED: onMicKey is no longer in LatinIME.onEvent's KeyCode.VOICE_INPUT branch"; fail=1; }
# Upstream's handoff must be gone, or the mic key would also switch to a voice IME.
if grep -q 'switchToShortcutIme(this)' "$L"; then
  echo "UPSTREAM CALL BACK: LatinIME calls switchToShortcutIme(this) again"; fail=1
fi
[ -f hebrish/android/core/src/main/java/app/hebrish/core/Transcriber.kt ] ||
  { echo "MISSING engine: hebrish/ submodule not checked out"; fail=1; }

[ $fail = 0 ] && echo "seams OK"
exit $fail
