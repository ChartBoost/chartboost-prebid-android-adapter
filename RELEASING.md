# Releasing

Maintainer-only notes on how a release actually happens. Publisher-facing versioning info lives in the
[README](README.md#versioning).

## Release flow

- **Release candidate (private):** push a `prerelease/**` branch, or run the `prerelease` workflow
  (`workflow_dispatch`). `scripts/get_rc_version.sh` computes the next `<version>-rcN` against the private
  repo, and the workflow publishes it there (`./gradlew ci :ChartboostPrebidPlugin:artifactoryPublish
  -PADAPTER_VERSION=<rc>`, `CHARTBOOST_PREBID_IS_RELEASE=false`). The `ci` aggregate runs the unit tests,
  assembles the release AAR, and verifies coordinate/embedded version agreement in the same invocation, so
  an RC can never publish untested code. The public repo is never touched by this path.
- **Public release:** push a `v<version>` tag on a `main` commit directly, or run `create-release-version`
  (`workflow_dispatch`, with optional SDK-pin inputs) to open a `release/<version>` PR that bumps
  `gradle.properties` and prepends a `CHANGELOG.md` entry. Merging that PR triggers `auto-release`, which
  pushes the `v<version>` tag on the commit the merge put on `main`. It waits for a reviewer only when the
  `release-approval` environment has one (see One-time setup). You can also run `auto-release` by hand, but
  only from `main`. The tag push triggers `release`, which checks the tagged commit is on `main`, verifies
  the tag equals the Gradle-computed version, runs the same `ci` test-and-verify gate before publishing the bare `<version>` to the public repo
  (`CHARTBOOST_PREBID_IS_RELEASE=true`), and cuts a GitHub Release with the AAR attached and that
  version's `CHANGELOG.md` section as the body.

The tag push itself (`release.yml`) has no separate reviewer gate (mirrors the Chartboost Mediation
adapters), but the `auto-release` approval step above and three automated guards back up tag discipline:

- The `verifyPublicReleaseVersion` Gradle task (a hard dependency of `artifactoryPublish`) fails any publish
  of an `-rc` version to the public repo, so a fat-fingered invocation cannot land an RC there.
- A GitHub tag ruleset restricts `v*` tag creation to repo admins. `auto-release` pushes tags with
  `RELEASE_PAT`, which must belong to an admin to pass it.
- `scripts/verify_on_main.sh` runs in both `auto-release` and `release` and fails unless the commit is on
  `main`, so a release can't go out by mistake from unmerged code.

The `main` checks live in the workflow files, and a run from a branch uses that branch's own files. So they
stop mistakes, not a writer who edits them on purpose. Only the environment rules below stop that.

Still: don't push a `v*` tag, and don't merge a `release/**` PR, before you mean to publish.

## One-time setup

- `JFROG_USER` / `JFROG_PASS` on a `CI` GitHub environment, with write access to `private-chartboost-ads`
  (RCs, used by `prerelease`) and `chartboost-ads` (public, used by `release`).
- Limit the `CI` environment's deployments to `main`, `prerelease/**`, and `v*` tags.
- A `release-approval` GitHub environment with a required reviewer and deployments limited to `main`, gating
  `auto-release`'s tag push.
- A `RELEASE_PAT` secret (`contents: write`) that `auto-release` uses to push the release tag and
  `create-release-version` uses to open the release-prep PR. Both need a PAT rather than the default
  `GITHUB_TOKEN`, because a tag or PR created by `GITHUB_TOKEN` does not trigger the downstream workflow.
  Keep it as a `release-approval` environment secret, not a repo secret, so a run from a branch can't read
  it. `create-release-version` then needs that environment too.
- "Allow GitHub Actions to create and approve pull requests" enabled in the repository settings, required by
  `create-release-version`.
