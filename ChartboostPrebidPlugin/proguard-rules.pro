# Consumer ProGuard rules for the Chartboost Prebid plugin renderer.
#
# Prebid Mobile discovers and drives the renderer through the public
# PrebidMobilePluginRenderer interface, so the public entry points and the
# ad-factory seam must survive R8.

-keep public class com.chartboost.prebid.ChartboostPrebidPluginRenderer { *; }
-keep public class com.chartboost.prebid.ChartboostPrebidRenderer { *; }
-keep public interface com.chartboost.prebid.internal.ChartboostAdFactory { *; }

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
