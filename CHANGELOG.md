# Changelog

Note the adapter version is computed from the host SDK versions it is certified with:
`(prebid_major × 100 + monetization_major).(monetization_minor).(monetization_patch × 100 + adapter_revision)`.

All official releases can be found on this repository's [releases page](https://github.com/ChartBoost/chartboost-prebid-android-adapter/releases).

<!-- releases -->

### 309.14.0
- This version of the adapter has been certified with Prebid Mobile SDK 3.3.1 and Chartboost Monetization SDK 9.14.0.
- Banner slots of at least 300x600 now render `HALFPAGE` instead of falling back to `MEDIUM` 300x250. Requires the host app to run Monetization SDK 9.14.0 or newer; on older hosts these slots keep resolving to `MEDIUM`.

### 309.12.0
- This version of the adapter has been certified with Prebid Mobile SDK 3.3.1 and Chartboost Monetization SDK 9.12.0.
- Initial release.
