#!/usr/bin/env bash
# Checks every seam in seams.txt (format there). Exit 1 and one line per failure if any is
# broken. Run from anywhere; paths are relative to the repo root.
set -uo pipefail
cd "$(dirname "$0")/.."
fail=0; n=0
while IFS=$'\t' read -r kind a b c d; do
  [[ -z "$kind" || "$kind" == \#* ]] && continue
  n=$((n+1))
  case "$kind" in
    has)    grep -qF -- "$b" "$a" 2>/dev/null || { echo "MISSING in $a: $b"; fail=1; } ;;
    lacks)  if grep -qF -- "$b" "$a" 2>/dev/null; then echo "UPSTREAM CODE BACK in $a: $b"; fail=1; fi ;;
    near)   grep -F -A "$c" -- "$b" "$a" 2>/dev/null | grep -qF -- "$d" ||
              { echo "MOVED in $a: '$d' not within $c lines after '$b'"; fail=1; } ;;
    exists) [ -e "$a" ] || { echo "MISSING path: $a"; fail=1; } ;;
    *)      echo "seams.txt: unknown kind '$kind'"; fail=1 ;;
  esac
done < seams.txt
[ $fail = 0 ] && echo "seams OK ($n checked)"
exit $fail
