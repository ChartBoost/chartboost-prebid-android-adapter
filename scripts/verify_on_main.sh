#!/usr/bin/env bash
#
# Copyright (c) 2026 Chartboost, Inc.
#
# Licensed under the MIT License.
#
# Fails unless a commit is on main. The release workflows run it before they tag or publish, so a release run
# by mistake from an unmerged commit stops here. It does not stop someone with write access who edits the
# workflow on a branch, because a branch run uses the branch's own workflow files. Only the environment rules
# in repo settings stop that.
#
# Usage: verify_on_main.sh [commit]. The commit defaults to HEAD. The clone needs origin/main with full history
# (actions/checkout with fetch-depth: 0).
set -euo pipefail

# The full ref name, so a tag or branch named "origin/main" can't stand in for it.
MAIN_REF="refs/remotes/origin/main"
commit="${1:-HEAD}"

if ! git rev-parse --verify --quiet "${MAIN_REF}" >/dev/null; then
  echo "::error::${MAIN_REF} is missing. Check out with fetch-depth: 0." >&2
  exit 1
fi

short="$(git rev-parse --short "${commit}")"
if ! git merge-base --is-ancestor "${commit}" "${MAIN_REF}"; then
  echo "::error::${short} is not on main. Release only from main." >&2
  exit 1
fi
echo "${short} is on main"
