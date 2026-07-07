# Changelog

`RENDERER_VERSION` is computed from the host SDK versions and registered with Prebid Mobile; the Prebid
Server adapter echoes it back from the request, so the two sides match by construction. The scheme is
`(prebid_sdk_major × 100 + monetization_major).(monetization_minor).(monetization_patch × 100 + adapter_revision)`.

All official releases will be on this repository's releases page. Each entry below is one released
`<version>`, newest first.

<!-- releases -->

### 309.12.0

- Initial Chartboost Prebid Android adapter: `PrebidMobilePluginRenderer` implementation routing
  Chartboost-flagged banner / interstitial / rewarded bids to the Chartboost Monetization SDK.
- Per-path URL firing (`UrlNotifier`) implemented and shipped disabled by default.
- `RENDERER_VERSION` is now computed from the host SDK versions (currently `309.12.0`) and echoed back by the
  Prebid Server adapter, replacing the static `1.0.0`.
- Added `ChartboostPrebidEventListener` (set via `ChartboostPrebidConfig.eventListener`) for plugin-level
  ad lifecycle callbacks, reported per `ChartboostAdFormat`.
- Added a Java-friendly `ChartboostPrebidConfig.Builder`.
- Added `ChartboostPrebidConfig.burlEnabled` (default false) to fire the impression-side `burl`
  independently, and `notificationResultListener` to observe each notification fire.
- Added `ChartboostPrebidRenderer.matchesServerRendererVersion(...)` so a publisher can assert the client
  version matches their Prebid Server stamp and fail fast instead of debugging a silent fallback.
- Fullscreen ad expiry now surfaces as a load failure (reload signal); failures and routing wins log at
  the default level, and the banner size mapper warns when it substitutes the nearest supported size.
- Build now fails early on a malformed `RENDERER_VERSION`.
- Release process mirrors the Chartboost Mediation adapters: pushing a `prerelease/**` branch publishes a
  private `<version>-rcN` (auto-incremented), and pushing a `v<version>` tag publishes the public release
  and cuts a GitHub Release. RCs stay private, so a bare version can never outrank the newest RC.
