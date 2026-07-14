# Consumer ProGuard rules for the Chartboost Prebid plugin renderer.
#
# Prebid Mobile discovers and drives the renderer through the public
# PrebidMobilePluginRenderer interface, so the two public entry points
# must survive R8.

-keep public class com.chartboost.prebid.ChartboostPrebidPluginRenderer { *; }
-keep public class com.chartboost.prebid.ChartboostPrebidRenderer { *; }
