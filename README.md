# Chartboost Prebid Android Adapter

A Prebid Mobile plugin renderer that hands Chartboost-flagged winning bids to the Chartboost
Monetization SDK for rendering, instead of Prebid's default renderer. Same Monetization SDK surface
that Chartboost Mediation and MAX drive, plugged into Prebid.

## Minimum Requirements

| Component                | Version |
| ------------------------ | ------- |
| Prebid Mobile SDK        | 3.3.1+  |
| Chartboost Monetization SDK | 9.12.0+ |
| Android API              | 21+     |
| kotlinx-coroutines-android | present at runtime (ships transitively with the Chartboost Monetization SDK) |

## How it works

Prebid Mobile routes a winning bid to a plugin renderer only when all of these hold:

1. The bid carries `ext.prebid.targeting` with `hb_pb` + `hb_bidder` (so the SDK treats it as the winner).
2. `ext.prebid.meta.rendererName` matches the plugin's `getName()` (`Chartboost-Android-SDK`).
3. `ext.prebid.meta.rendererVersion` matches the plugin's `getVersion()`, byte for byte.

On a match, the plugin builds a Chartboost `Banner` / `Interstitial` / `Rewarded`, hands it the bid's
`adm` via `cache()`, and renders. A mismatch on any key falls back to Prebid's default renderer.

## Integration

The Chartboost Monetization SDK and this adapter are hosted on Chartboost's Maven repository, not Maven
Central, so declare it alongside `mavenCentral()` (which serves Prebid Mobile and coroutines). In your
`settings.gradle`:

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url "https://cboost.jfrog.io/artifactory/chartboost-ads" }
    }
}
```

Then in your app's `build.gradle`, add the adapter plus the SDKs it renders through. The adapter is published
`compileOnly` against both SDKs, so your app must depend on them directly:

```groovy
implementation "com.chartboost:chartboost-prebid-adapter:309.12.0"
implementation "com.chartboost:chartboost-sdk:9.12.0"
implementation "org.prebid:prebid-mobile-sdk:3.3.1"
```

The adapter uses Kotlin coroutines at runtime and expects `kotlinx-coroutines-android` on the app
classpath, which the Chartboost Monetization SDK already provides transitively; no extra dependency is
needed unless your build excludes it.

Then register the renderer once before initializing Prebid:

```kotlin
ChartboostPrebidRenderer.register()
PrebidMobile.initializeSdk(context, "https://<your-pbs-host>/openrtb2/auction") { status ->
    Log.d("prebid", "init: $status")
}
```

Then load a rendering ad unit as usual (`BannerView` / `InterstitialAdUnit` / `RewardedAdUnit`); a
Chartboost-flagged bid routes to this plugin automatically.

## Consent and privacy

This adapter does not collect, store, or forward any consent signals. GDPR, US Privacy (CCPA), COPPA, and
GPP are owned by the Chartboost Monetization SDK. The Chartboost Monetization SDK will collect these
signals, if available from consent management platforms. Configure your regulatory signals via your consent
management platform before loading ads, exactly as you would for any other Chartboost integration; see the 
Monetization SDK's own documentation for how. Plugin-rendered ads render through that same SDK instance, 
so its consent state applies to them as well.

## Repository layout

| Path                     | What it is |
| ------------------------ | ---------- |
| `ChartboostPrebidPlugin/` | The adapter library (the shipped artifact). |

## Versioning

`BuildConfig.RENDERER_VERSION` is computed in the root `build.gradle.kts` from the scheme
`(prebid_sdk_major × 100 + monetization_major).(monetization_minor).(monetization_patch × 100 + adapter_revision)`,
using `chartboostSdkVersion` / `prebidMobileVersion` / `adapterRevision` (currently `309.12.0`). The Prebid
Server adapter echoes this value back from the request, so the client-registered version and the
server-stamped version match by construction — no shared constant or lock-step release. Override with
`-PRENDERER_VERSION=X.Y.Z` to pin it.

The release process mirrors the Chartboost Mediation adapters:

- **Release candidate (private):** push a `prerelease/**` branch, or run the `prerelease` workflow.
  `scripts/get_rc_version.sh` picks the next `<version>-rcN` against the private repo and publishes it
  there. RCs are always private, so a dynamic `+` / `-rc+` consumer always resolves the newest RC rather
  than a stale base version outranking it.
- **Public release:** push a `v<version>` tag, or run `create-release-version` to open a `release/<version>`
  PR that the `auto-release` workflow tags for you on merge. The `release` workflow verifies the tag equals
  the computed version, publishes the bare `<version>` to the public repo, and cuts a GitHub Release with
  the AAR.

The `-rcN` tail and the bare version both ride through the server echo, so the client-registered and
server-stamped versions still match.

**Release setup (one time).** The workflows need `JFROG_USER` / `JFROG_PASS` on a `CI` GitHub environment,
with write access to `private-chartboost-ads` (RCs) and `chartboost-ads` (public), plus a repo/org
`RELEASE_PAT` secret (`contents: write`) that `auto-release` uses to push the tag and `create-release-version`
uses to open the PR. Both need a PAT rather than the default `GITHUB_TOKEN`, because a tag or PR created by
`GITHUB_TOKEN` does not trigger the downstream workflow. `create-release-version` also requires "Allow GitHub
Actions to create and approve pull requests" to be enabled in the repository settings.

## License

MIT. See [LICENSE.md](LICENSE.md).
