#!/usr/bin/env bash
# Merge the newest stable upstream release into the current branch, reset REVISION to 1 and
# check the seams. Used by .github/workflows/fork-upstream-sync.yml; also safe to run by hand.
#
# Prints key=value lines on stdout for the workflow (latest=, status=, tag=, conflicts=).
#   status=current   upstream's newest stable tag is already merged; nothing done
#   status=merged    merged and committed, seams OK; tag= is the release tag to push
#   status=conflict  merge aborted; conflicts= lists the files (space-separated)
#   status=seams     merged and committed but a seam is broken (details on stderr); not tagged
set -uo pipefail
cd "$(dirname "$0")/.."
source ./fork.env

git remote get-url upstream >/dev/null 2>&1 || git remote add upstream "$UPSTREAM_URL"
git fetch -q upstream --tags
latest=$(git ls-remote --tags --refs upstream | sed 's#.*refs/tags/##' \
  | grep -E "$UPSTREAM_TAG_REGEX" | sort -V | tail -1)
[ -n "$latest" ] || { echo "no upstream tag matches $UPSTREAM_TAG_REGEX" >&2; exit 2; }
echo "latest=$latest"

if git merge-base --is-ancestor "$latest" HEAD; then echo "status=current"; exit 0; fi

if ! git merge --no-edit -m "Merge upstream $latest" "$latest" >&2; then
  files=$(git diff --name-only --diff-filter=U | tr '\n' ' ')
  git merge --abort
  echo "status=conflict"; echo "conflicts=$files"; exit 0
fi
sed -i 's/^REVISION=.*/REVISION=1/' fork.env
git commit -qam "$SUFFIX revision 1 on upstream $latest" || true
if ! scripts/check-seams.sh >&2; then echo "status=seams"; exit 0; fi
echo "status=merged"; echo "tag=${latest}-${SUFFIX}.1"
