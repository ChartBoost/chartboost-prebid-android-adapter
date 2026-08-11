/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

plugins {
    id("com.android.library") version "9.3.1" apply false
    id("org.jetbrains.kotlin.android") version "2.3.0" apply false
    // Applied in :ChartboostPrebidPlugin for the Artifactory publish (private RCs and the public release).
    id("com.jfrog.artifactory") version "4.32.0" apply false
}

// Adapter version: one semver encoding the host Prebid SDK major and the full Chartboost Monetization SDK
// version, computed once here and stamped into the adapter's BuildConfig.ADAPTER_VERSION. A Monetization
// bump moves the value automatically; the Prebid Server adapter echoes it back from the request, so the two
// sides match by construction with no lock-step release. Override with -PADAPTER_VERSION=X.Y.Z to pin it.
// The inline ?: defaults mirror gradle.properties so this still evaluates when a property is absent (e.g.
// `./gradlew tasks` with no -P flags).
val adapterVersion: String = ((findProperty("ADAPTER_VERSION") as String?) ?: run {
    val prebidMajorScale = 100 // lifts the Prebid major into the hundreds place of the first component
    val monPatchScale = 100    // lifts the Monetization patch into the hundreds place of the third component
    val monPin = (findProperty("chartboostSdkVersion") as String?) ?: "9.12.0"
    val prebidPin = (findProperty("prebidMobileVersion") as String?) ?: "3.3.1"
    fun intPart(value: String, label: String): Int =
        value.trim().toIntOrNull() ?: error("$label '$value' must be a non-negative integer")
    val adapterRevision = (findProperty("adapterRevision") as String?)?.let { intPart(it, "adapterRevision") } ?: 0
    val mon = monPin.split('.')
    require(mon.size >= 3) { "chartboostSdkVersion '$monPin' must be MAJOR.MINOR.PATCH" }
    val prebidMajor = intPart(prebidPin.substringBefore('.'), "prebidMobileVersion major")
    val monMajor = intPart(mon[0], "chartboostSdkVersion major")
    val monMinor = intPart(mon[1], "chartboostSdkVersion minor")
    val monPatch = intPart(mon[2].substringBefore('-'), "chartboostSdkVersion patch") // tolerate a -suffix
    // Bound the two components that share a place-value slot with another component, so two different
    // SDK-pin states can never encode the identical ADAPTER_VERSION string.
    require(monMajor in 0..99) { "chartboostSdkVersion major '$monMajor' must be 0-99 so it cannot collide with the Prebid-major component of ADAPTER_VERSION" }
    require(adapterRevision in 0..99) { "adapterRevision '$adapterRevision' must be 0-99 so it cannot collide with the Monetization-patch component of ADAPTER_VERSION" }
    "${prebidMajor * prebidMajorScale + monMajor}.$monMinor.${monPatch * monPatchScale + adapterRevision}"
}).also {
    // Registered with Prebid Mobile, so it must be canonical X.Y.Z semver, optionally with a release-
    // candidate "-rc<N>" pre-release tail (N ≥ 1). By convention the "-rc<N>" versions ship privately and
    // canonical versions publicly; the repo choice itself is gated by CHARTBOOST_PREBID_IS_RELEASE in the
    // module's publishing block. A bad value (empty, leading zeros, non-numeric) registers an adapter
    // version the server's echo can never reproduce, silently falling back to Prebid's own rendering.
    // The "-rc<N>" tail rides through that same echo, so an RC client and an RC-stamped server still match
    // by construction.
    require(it.matches(Regex("""^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(-rc[1-9]\d*)?$"""))) {
        "ADAPTER_VERSION '$it' is not canonical X.Y.Z semver with an optional -rc<N> tail (no leading zeros)."
    }
}
extra["adapterVersion"] = adapterVersion

// Surfaces the computed base version on stdout so the RC-version script can build "<version>-rc<N>" without
// re-deriving the formula. Run with -q so only the version prints: ./gradlew -q printAdapterVersion
tasks.register("printAdapterVersion") {
    val resolved = adapterVersion
    doLast { println(resolved) }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

// Aggregate CI entry point, mirroring the Chartboost Mediation adapters' `./gradlew ci`. Runs the unit
// tests, assembles the release AAR, and asserts the published coordinate matches the embedded
// ADAPTER_VERSION. A fresh CI checkout needs no clean, so this deliberately does not depend on it.
tasks.register("ci") {
    group = "verification"
    description = "Runs unit tests, assembles the release AAR, and verifies version agreement."
    dependsOn(
        ":ChartboostPrebidPlugin:testDebugUnitTest",
        ":ChartboostPrebidPlugin:assembleRelease",
        ":ChartboostPrebidPlugin:verifyPublishedVersion",
    )
}
