# Consumer ProGuard rules for the Chartboost Prebid plugin renderer.
#
# Prebid Mobile discovers and drives the renderer through the public
# PrebidMobilePluginRenderer interface, so both entry points must
# survive R8. ChartboostPrebidPluginRenderer is Kotlin-internal but
# compiles to a public JVM class, so its keep rule still matches.

-keep public class com.chartboost.prebid.ChartboostPrebidPluginRenderer { *; }
-keep public class com.chartboost.prebid.ChartboostPrebidRenderer { *; }
