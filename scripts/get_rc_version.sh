#!/usr/bin/env bash
#
# Copyright (c) 2026 Chartboost, Inc.
#
# Licensed under the MIT License.
#
# Computes the next release-candidate version for the Chartboost Prebid adapter, mirroring the Chartboost
# Mediation adapters' convention: "<base version>-rc<N>", where N auto-increments. It queries the private
# Artifactory for every existing "-rc" of the current base version and returns the highest N plus one (rc1
# when none exist yet). RC builds live only in the private repo, so the repo key is hardcoded to it.
#
# Requires JFROG_USER and JFROG_PASS in the environment. Prints only the next RC version to stdout; progress
# and errors go to stderr so the caller can capture a clean version string.
set -euo pipefail

ARTIFACTORY_HOST="cboost.jfrog.io"
REPO_KEY="private-chartboost-ads"
GROUP_PATH="com/chartboost/chartboost-prebid-adapter"
METADATA_URL="https://${ARTIFACTORY_HOST}/artifactory/${REPO_KEY}/${GROUP_PATH}/maven-metadata.xml"

: "${JFROG_USER:?JFROG_USER must be set}"
: "${JFROG_PASS:?JFROG_PASS must be set}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "${SCRIPT_DIR}")"

# Base version straight from Gradle, so the version formula is never duplicated here. tail -n1 guards against
# any stray daemon output sneaking onto stdout ahead of the version line.
BASE_VERSION="$("${REPO_ROOT}/gradlew" -q -p "${REPO_ROOT}" printRendererVersion | tail -n1 | tr -d '[:space:]')"
if [ -z "${BASE_VERSION}" ]; then
  echo "could not resolve the base renderer version from Gradle" >&2
  exit 1
fi
RELEASE_CANDIDATE="${BASE_VERSION}-rc"
# Escape the dots so the base version matches literally in the regex below, not as wildcards.
ESCAPED_BASE="$(printf '%s' "${BASE_VERSION}" | sed 's/\./\\./g')"

# Fetch the published metadata, distinguishing the legitimate "nothing published yet" case (HTTP 404 -> first
# RC) from real failures (auth, DNS, timeout, 5xx). Swallowing every failure would silently reset the counter
# to rc1 and overwrite an existing RC, so anything other than 200 or 404 aborts.
METADATA_FILE="$(mktemp)"
NETRC_FILE="$(mktemp)"
chmod 600 "${NETRC_FILE}"
trap 'rm -f "${METADATA_FILE}" "${NETRC_FILE}"' EXIT
# Pass credentials via a 0600 netrc file rather than -u, so they never land in the process argument list.
printf 'machine %s login %s password %s\n' "${ARTIFACTORY_HOST}" "${JFROG_USER}" "${JFROG_PASS}" > "${NETRC_FILE}"
HTTP_STATUS="$(curl -sS --netrc-file "${NETRC_FILE}" -o "${METADATA_FILE}" -w '%{http_code}' "${METADATA_URL}" || echo "000")"
case "${HTTP_STATUS}" in
  200) METADATA="$(cat "${METADATA_FILE}")" ;;
  404) METADATA="" ;; # nothing published yet -> first RC
  *) echo "failed to fetch ${METADATA_URL} (HTTP ${HTTP_STATUS})" >&2; exit 1 ;;
esac

# Find every published "<version><base>-rc<N></version>", take the highest N, add one. The full-tag anchor
# plus the escaped base keep a different version that merely contains this one as a substring from leaking in.
# No matches -> max stays 0 -> rc1.
#
# grep exits 1 when nothing matches, which is the legitimate first-RC case (empty or RC-less metadata). awk's
# END block always emits the count, so the pipeline's stdout is correct regardless; the trailing `|| true`
# keeps that benign grep miss from aborting the script under `set -o pipefail`.
NEXT="$(printf '%s\n' "${METADATA}" \
  | grep -oE "<version>${ESCAPED_BASE}-rc[0-9]+</version>" \
  | grep -oE 'rc[0-9]+' \
  | sed 's/^rc//' \
  | awk 'BEGIN { max = 0 } { if ($0 + 0 > max) max = $0 + 0 } END { print max + 1 }' || true)"

echo "${RELEASE_CANDIDATE}${NEXT}"
