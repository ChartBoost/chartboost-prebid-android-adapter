#
# Copyright (c) 2026 Chartboost, Inc.
#
# Licensed under the MIT License.
#

# Consumer ProGuard rules for the Chartboost Prebid plugin adapter.
#
# Prebid Mobile discovers and drives the adapter through the public
# PrebidMobilePluginRenderer interface, so both entry points must
# survive R8. ChartboostPrebidPluginAdapter is Kotlin-internal but
# compiles to a public JVM class, so its keep rule still matches.

-keep public class com.chartboost.prebid.ChartboostPrebidPluginAdapter { *; }
-keep public class com.chartboost.prebid.ChartboostPrebidAdapter { *; }

# The banner adapter recognizes an AdMob or MAX ad view above it by class name, not by type, so it needs no
# dependency on either SDK. R8 must not rename those classes in a minified app, or the match silently fails
# and a view parked in a list or pager is torn down. The dontwarn lines keep apps that ship neither SDK
# building.
-keepnames class com.google.android.gms.ads.BaseAdView
-keepnames class com.applovin.mediation.ads.MaxAdView
-dontwarn com.google.android.gms.ads.BaseAdView
-dontwarn com.applovin.mediation.ads.MaxAdView
