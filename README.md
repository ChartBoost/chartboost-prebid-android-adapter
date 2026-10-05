# Chartboost Prebid Android Adapter

A Prebid Mobile plugin adapter that hands Chartboost-flagged winning bids to the Chartboost
Monetization SDK for rendering, instead of letting Prebid render them. Same Monetization SDK surface
that Chartboost Mediation and MAX drive, plugged into Prebid.

## Minimum Requirements

| Component                | Version |
| ------------------------ | ------- |
| Prebid Mobile SDK        | 3.3.1+  |
| Chartboost Monetization SDK | 9.14.0+ |
| Android API              | 21+     |
| kotlinx-coroutines-android | present at runtime (ships transitively with the Chartboost Monetization SDK) |

## How it works

Prebid Mobile routes a winning bid to a registered plugin only when all of these hold:

1. The bid carries `ext.prebid.targeting` with `hb_pb` + `hb_bidder` (so the SDK treats it as the winner).
2. `ext.prebid.meta.rendererName` matches the plugin's `getName()` (`Chartboost-Android-SDK`).
3. `ext.prebid.meta.rendererVersion` matches the plugin's `getVersion()`, byte for byte.

On a match, the plugin builds a Chartboost `Banner` / `Interstitial` / `Rewarded`, hands it the bid's
`adm` via `cache()`, and renders. A mismatch on any key falls back to Prebid's own rendering.

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
implementation "com.chartboost:chartboost-prebid-adapter:309.14.0"
implementation "com.chartboost:chartboost-sdk:9.14.0"
implementation "org.prebid:prebid-mobile-sdk:3.3.1"
```

The adapter uses Kotlin coroutines at runtime and expects `kotlinx-coroutines-android` on the app
classpath, which the Chartboost Monetization SDK already provides transitively; no extra dependency is
needed unless your build excludes it.

Then register the adapter once before initializing Prebid:

```kotlin
ChartboostPrebidAdapter.register()
PrebidMobile.initializeSdk(context, "https://<your-pbs-host>/openrtb2/auction") { status ->
    Log.d("prebid", "init: $status")
}
```

Then load a rendering ad unit as usual (`BannerView` / `InterstitialAdUnit` / `RewardedAdUnit`); a
Chartboost-flagged bid routes to this plugin automatically.

## Configuration

`register()` takes an optional `ChartboostPrebidConfig`. Every field has a default that reproduces the
bare `register()` behavior, so you only need to set what you want to change:

```kotlin
ChartboostPrebidAdapter.register(
    ChartboostPrebidConfig(
        location = "Home_Interstitial",
        logLevel = LogLevel.DEBUG,
        eventListener = myEventListener,
    ),
)
```

From Java, build the same config with `ChartboostPrebidConfig.Builder` (Kotlin default arguments aren't
visible to Java callers); each setter returns `this`.

| Field | Default | What it does |
| ----- | ------- | ------------- |
| `location` | `"Prebid"` | Chartboost ad location tag attached to every plugin-rendered ad. A blank value coerces back to the default. Override to break out placements in Chartboost reporting. |
| `logLevel` | `LogLevel.WARN` | Plugin log verbosity; see below. |
| `eventListener` | `null` | Optional callback for plugin-rendered ad lifecycle events (see below). |

`LogLevel` is ordered least to most verbose:

| Level | Gates |
| ----- | ----- |
| `NONE` | Nothing — no warnings, no integration-info line. |
| `WARN` (default) | Warnings, plus the one-line output of `logIntegrationInfo()`. |
| `DEBUG` | Adds verbose per-ad debug logging on top of `WARN`. |

`ChartboostPrebidEventListener` reports what the plugin itself did with an ad, separately from Prebid's own
per-ad-unit listeners — useful for analytics or for telling a real adapter failure apart from a silent
routing fallback. All callbacks run on the main thread and take a `ChartboostAdFormat`
(`BANNER` / `INTERSTITIAL` / `REWARDED`):

- `onAdLoaded(format)`
- `onAdDisplayed(format)` — the ad became visible. Fires on the Chartboost show event for `INTERSTITIAL` /
  `REWARDED`, and on the first recorded impression for `BANNER` (banners have no separate show signal).
- `onAdClicked(format)`
- `onAdFailed(format, error)` — `error` is Prebid's `AdException`.
- `onAdDismissed(format)` — fullscreen only; banners never invoke it.
- `onUserEarnedReward(format)` — fullscreen only; banners never invoke it.

Prebid's own ad lifecycle has no terminal show-failed signal, so a fullscreen ad that loads but then fails
at show time (e.g. it expired before `show()`) is logged but does not invoke `onAdFailed`; don't treat a
missing display as a guaranteed failure callback.

`ChartboostPrebidAdapter` also exposes three integration helpers:

- `unregister()` — the counterpart to `register()`; a no-op when nothing is registered. Call it to tear
  down a registration whose `eventListener` captures an Activity, so the listener does not outlive it.
- `matchesServerAdapterVersion(prebidServerAdapterVersion)` — returns whether `adapterVersion` exactly,
  byte-for-byte, equals the value your Prebid Server adapter stamps as `ext.prebid.meta.rendererVersion`. A
  mismatch makes Prebid Mobile silently fall back to its own rendering, so assert on this during
  integration to fail fast instead of debugging a silent fallback.
- `logIntegrationInfo()` — logs the registered adapter name and version at `WARN`-or-above verbosity, for
  the same purpose.

## Banner sizes

The Chartboost Monetization SDK renders a banner at one of its own `Banner.BannerSize` dimensions and
nothing else:

| Size | Dimensions | Available from |
| --- | --- | --- |
| `STANDARD` | 320x50 | all supported versions |
| `MEDIUM` | 300x250 | all supported versions |
| `LEADERBOARD` | 728x90 | all supported versions |
| `HALFPAGE` | 300x600 | Monetization SDK 9.14.0+ |

Given a winning bid's negotiated width and height, this adapter selects the **largest of those sizes that
fits entirely inside** the requested dimensions. A full-width slot of, say, 412x50 renders `STANDARD`
320x50 within it, the same way a fixed size is fitted into an adaptive banner slot.

**Bid with no declared size.** Prebid reads a missing width/height as 0x0, and Chartboost's own demand
omits width/height on banner bids today. Rather than treat that as an automatic no-fill, the adapter falls
back to the size configured on the ad unit itself — either `BannerParameters.adSizes` or the older
deprecated size setters, matching the precedence Prebid Mobile uses when it builds the request. The
fallback also applies when only one dimension is missing (e.g. 320x0), since that is just as unusable as
0x0. If the ad unit offers more than one size, the response does not say which one the bid actually won,
so the adapter picks the smallest by area and logs a warning naming the candidates, since the smallest is
the only choice that cannot overflow a slot the publisher sized for something smaller. Sizes the
Monetization SDK cannot render into are passed over first, so an ad unit offering both 320x50 and 50x320
resolves to 320x50 rather than declining the bid. A bid that already carries a usable width and height is
unaffected; the ad unit's configured size is never consulted for it.

This substitution decides which `Banner.BannerSize` gets rendered. It does not rewrite the bid, so
anything Prebid Mobile derives from the bid's own width and height, such as the `hb_size` targeting key,
still reflects what the bidder sent. Reporting a real width and height on the bid remains a server-side
concern.

If nothing fits, the bid is declined through `DisplayViewListener.onAdFailed`. That is final, not a
retry: Prebid Mobile's `DisplayView.createBannerAdView` picks a plugin once, via
`getPluginForPreferredRenderer`, and once our plugin has that bid, a decline chains straight to
`BannerViewListener.onAdFailed` on the publisher's listener. There is no second rendering attempt and no
fallback to Prebid's own renderer. A 320x49 slot is a no-fill, because even the shortest Chartboost banner
needs 50dp of height. The adapter never renders a size larger than the slot it was given: doing so would
still count a billable impression while overflowing the publisher's layout.

The size set is read from the host SDK at runtime, so a size added in a later Monetization SDK release
becomes selectable without an adapter release. The Chartboost Prebid Server adapter has to offer that size
in the auction as well, so a newly added size needs its list updated too before it can win a bid.

If you see unexpected banner no-fills, the usual cause is a slot too small for any Chartboost size. The
Chartboost Prebid Server adapter filters each impression's `imp.Banner.format` down to the sizes it can
render and rejects a banner-only impression that offers none of them, so most size mismatches surface
upstream rather than as a no-fill on the device. An impression that also carries video keeps its video slot
and loses only the banner.

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
| `scripts/`                | Release-automation helpers (`get_rc_version.sh`, see [Versioning](#versioning)). |

## Building from source

Requires JDK 17. `./gradlew ci` runs the same verification gate CI runs; [RELEASING.md](RELEASING.md)
describes what it covers.

## Versioning

`BuildConfig.ADAPTER_VERSION` is computed in the root `build.gradle.kts` from the scheme
`(prebid_sdk_major × 100 + monetization_major).(monetization_minor).(adapter_revision)`,
using `chartboostSdkVersion` / `prebidMobileVersion` / `adapterRevision` (currently `309.14.0`). The
Monetization patch is not encoded, the same as the iOS adapter: the adapter supports every patch of its
certified Monetization minor, so a Monetization patch release needs no new adapter version. The Prebid
Server adapter echoes this value back from the request, so the client-registered version and the
server-stamped version match by construction — no shared constant or lock-step release. Override with
`-PADAPTER_VERSION=X.Y.Z` to pin it.

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

Maintainers: see [RELEASING.md](RELEASING.md) for the one-time setup and how each workflow fits together.

## License

MIT. See [LICENSE.md](LICENSE.md).
