# Releasing

Maintainer-only notes on how a release actually happens. Publisher-facing versioning info lives in the
[README](README.md#versioning).

## Release flow

- **Release candidate (private):** push a `prerelease/**` branch, or run the `prerelease` workflow
  (`workflow_dispatch`). `scripts/get_rc_version.sh` computes the next `<version>-rcN` against the private
  repo, and the workflow publishes it there (`./gradlew ci :ChartboostPrebidPlugin:artifactoryPublish
  -PRENDERER_VERSION=<rc>`, `CHARTBOOST_PREBID_IS_RELEASE=false`). The `ci` aggregate runs the unit tests,
  assembles the release AAR, and verifies coordinate/embedded version agreement in the same invocation, so
  an RC can never publish untested code. The public repo is never touched by this path.
- **Public release:** push a `v<version>` tag directly, or run `create-release-version`
  (`workflow_dispatch`, with optional SDK-pin inputs) to open a `release/<version>` PR that bumps
  `gradle.properties` and prepends a `CHANGELOG.md` entry. Merging that PR triggers `auto-release`, which
  pushes the `v<version>` tag. The tag push triggers `release`, which verifies the tag equals the
  Gradle-computed version, runs the same `ci` test-and-verify gate before publishing the bare `<version>`
  to the public repo (`CHARTBOOST_PREBID_IS_RELEASE=true`), and cuts a GitHub Release with the AAR attached.

There is no PR-approval-style reviewer gate on the tag-push path itself (mirrors the Chartboost Mediation
adapters), but two automated guards back up tag discipline:

- The `verifyPublicReleaseVersion` Gradle task (a hard dependency of `artifactoryPublish`) fails any publish
  of an `-rc` version to the public repo, so a fat-fingered invocation cannot land an RC there.
- A GitHub tag ruleset restricts `v*` tag creation to repo admins, so only they can trigger a public release.

Still: don't push a `v*` tag, and don't merge a `release/**` PR, before you mean to publish.

## One-time setup

- `JFROG_USER` / `JFROG_PASS` on a `CI` GitHub environment, with write access to `private-chartboost-ads`
  (RCs, used by `prerelease`) and `chartboost-ads` (public, used by `release`).
- A repo/org `RELEASE_PAT` secret (`contents: write`) that `auto-release` uses to push the release tag and
  `create-release-version` uses to open the release-prep PR. Both need a PAT rather than the default
  `GITHUB_TOKEN`, because a tag or PR created by `GITHUB_TOKEN` does not trigger the downstream workflow.
- "Allow GitHub Actions to create and approve pull requests" enabled in the repository settings, required by
  `create-release-version`.
