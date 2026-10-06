#!/usr/bin/env bash
#
# Copyright (c) 2026 Chartboost, Inc.
#
# Licensed under the MIT License.
#
# Tests verify_on_main.sh against throwaway git repos: a bare "origin" with a main branch and an unmerged
# feature branch, and a full clone of it. Prints one line per case and exits non-zero if any case fails.
set -euo pipefail

SCRIPT="$(cd "$(dirname "$0")" && pwd)/verify_on_main.sh"
WORK="$(mktemp -d)"
failures=0

git_quiet() { git -c user.name=test -c user.email=test@example.com "$@" >/dev/null 2>&1; }

# origin has two commits on main, plus one commit on a branch that was never merged.
git_quiet init --bare -b main "${WORK}/origin.git"
git_quiet clone "${WORK}/origin.git" "${WORK}/seed"
git_quiet -C "${WORK}/seed" commit --allow-empty -m "first"
git_quiet -C "${WORK}/seed" commit --allow-empty -m "second"
git_quiet -C "${WORK}/seed" push origin main
git_quiet -C "${WORK}/seed" switch -c feature
git_quiet -C "${WORK}/seed" commit --allow-empty -m "unmerged"
git_quiet -C "${WORK}/seed" push origin feature
FEATURE_COMMIT="$(git -C "${WORK}/seed" rev-parse HEAD)"
MAIN_PARENT_COMMIT="$(git -C "${WORK}/seed" rev-parse main~1)"

git_quiet clone "${WORK}/origin.git" "${WORK}/clone"
# A tag named like the remote branch, pointing at the unmerged commit.
git_quiet clone "${WORK}/origin.git" "${WORK}/clone-with-tag"
git_quiet -C "${WORK}/clone-with-tag" tag origin/main "${FEATURE_COMMIT}"
git_quiet init -b main "${WORK}/no-origin"
git_quiet -C "${WORK}/no-origin" commit --allow-empty -m "local only"

expect() {
  local name="$1" want="$2" dir="$3"
  shift 3
  local got=pass
  (cd "${dir}" && "${SCRIPT}" "$@" >/dev/null 2>&1) || got=fail
  if [ "${got}" = "${want}" ]; then
    echo "ok: ${name}"
  else
    echo "FAILED: ${name} (wanted ${want}, got ${got})"
    failures=$((failures + 1))
  fi
}

expect "the main tip passes" pass "${WORK}/clone"
expect "an older commit on main passes" pass "${WORK}/clone" "${MAIN_PARENT_COMMIT}"
expect "a commit only on an unmerged branch fails" fail "${WORK}/clone" "${FEATURE_COMMIT}"
expect "a tag named origin/main does not make an unmerged commit pass" fail "${WORK}/clone-with-tag" "${FEATURE_COMMIT}"
expect "a clone without origin/main fails" fail "${WORK}/no-origin"

exit "${failures}"
