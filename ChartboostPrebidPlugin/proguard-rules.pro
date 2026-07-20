# Consumer ProGuard rules for the Chartboost Prebid plugin adapter.
#
# Prebid Mobile discovers and drives the adapter through the public
# PrebidMobilePluginRenderer interface, so both entry points must
# survive R8. ChartboostPrebidPluginAdapter is Kotlin-internal but
# compiles to a public JVM class, so its keep rule still matches.

-keep public class com.chartboost.prebid.ChartboostPrebidPluginAdapter { *; }
-keep public class com.chartboost.prebid.ChartboostPrebidAdapter { *; }
