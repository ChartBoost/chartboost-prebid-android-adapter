/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
    id("com.jfrog.artifactory")
}

val chartboostSdkVersion: String = (project.findProperty("chartboostSdkVersion") as String?) ?: "9.14.0"
val prebidMobileVersion: String = (project.findProperty("prebidMobileVersion") as String?) ?: "3.3.1"

// Adapter version, computed and validated once in the root build. Stamped into
// BuildConfig.ADAPTER_VERSION and registered with Prebid Mobile; the Prebid Server adapter echoes it back
// from the request, so the two sides match by construction.
val adapterVersion: String = rootProject.extra["adapterVersion"] as String

// The published Maven artifact id. Reused by the publication and by verifyPublishedVersion, which anchors
// its POM-version match on this exact coordinate.
val publishedArtifactId = "chartboost-prebid-adapter"

android {
    namespace = "com.chartboost.prebid"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        buildConfigField("String", "ADAPTER_VERSION", "\"$adapterVersion\"")
        consumerProguardFiles("proguard-rules.pro")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            consumerProguardFiles("proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
        getByName("test").java.srcDirs("src/test/kotlin")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Host app ships both SDKs (and coroutines, transitively via the Chartboost SDK); the adapter compiles
    // against them but does not bundle them.
    compileOnly("com.chartboost:chartboost-sdk:$chartboostSdkVersion")
    compileOnly("org.prebid:prebid-mobile-sdk:$prebidMobileVersion")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")

    testImplementation("com.chartboost:chartboost-sdk:$chartboostSdkVersion")
    testImplementation("org.prebid:prebid-mobile-sdk:$prebidMobileVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}

// ---- Publishing ------------------------------------------------------------------------------------
// Publishes the release AAR as com.chartboost:chartboost-prebid-adapter:<adapterVersion> so consuming
// builds can resolve it. The version reuses the computed adapter version, so the artifact and
// BuildConfig.ADAPTER_VERSION never drift. Deps are compileOnly (the host app ships both SDKs), so the
// generated POM carries no transitive dependencies, by design.
//
// Destination follows the same release/non-release split the SDKs use. A publish targets the public
// "chartboost-ads" repo only when CHARTBOOST_PREBID_IS_RELEASE is "true". With the flag unset (the default
// for tag pushes and ordinary dispatches) every publish goes to the private "private-chartboost-ads" repo,
// so a publish reaches the public repo only when a release deliberately opts in. The
// -PprebidArtifactoryRepoKey=<key> override takes precedence over the flag and can point at either repo
// directly, so it is reserved for rare layout-specific cases run by hand.
val publicRepoKey = "chartboost-ads"
val privateRepoKey = "private-chartboost-ads"
val isPublicReleaseBuild = "true" == System.getenv("CHARTBOOST_PREBID_IS_RELEASE")
val artifactoryRepoKey: String = (findProperty("prebidArtifactoryRepoKey") as String?)
    ?: if (isPublicReleaseBuild) publicRepoKey else privateRepoKey

artifactory {
    // Don't collect the build's env vars into the published build-info: the deploy credential lives in
    // JFROG_PASS, which the plugin's default scrubber (*password*,*secret*,...) does NOT match, so it
    // would otherwise be uploaded alongside the artifact. We don't need env vars in build-info anyway.
    clientConfig.isIncludeEnvVars = false
    setContextUrl("https://cboost.jfrog.io/artifactory")
    publish {
        repository {
            setRepoKey(artifactoryRepoKey)
            setUsername(System.getenv("JFROG_USER"))
            setPassword(System.getenv("JFROG_PASS"))
        }
        defaults {
            publications("release")
            setPublishArtifacts(true)
            setPublishPom(true)
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                artifact(layout.buildDirectory.file("outputs/aar/${project.name}-release.aar"))
                groupId = "com.chartboost"
                artifactId = publishedArtifactId
                version = adapterVersion

                pom {
                    name.set("Chartboost Prebid Adapter")
                    description.set("Chartboost adapter for Prebid Mobile on Android.")
                    url.set("https://www.chartboost.com/")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                    developers {
                        developer {
                            id.set("chartboostmobile")
                            name.set("chartboost mobile")
                            email.set("support@chartboost.com")
                        }
                    }
                    scm {
                        val gitUrl = "https://github.com/ChartBoost/chartboost-prebid-android-adapter/"
                        url.set(gitUrl)
                        // scm:git:<url> is the canonical Maven SCM connection format; the bare browse URL
                        // (used for scm.url above) is not a valid connection/developerConnection value.
                        val scmGitUrl = "scm:git:${gitUrl.trimEnd('/')}.git"
                        connection.set(scmGitUrl)
                        developerConnection.set(scmGitUrl)
                    }
                }
            }
        }
    }

    // Guards against the Maven coordinate version and the compiled BuildConfig.ADAPTER_VERSION drifting
    // apart. They share one source today, but a publish advertising one version while the binary registers
    // another would route to nobody, so assert it on the generated artifacts before any publish.
    val verifyPublishedVersion = tasks.register("verifyPublishedVersion") {
        dependsOn("assembleRelease", "generatePomFileForReleasePublication")
        val pomFile = layout.buildDirectory.file("publications/release/pom-default.xml")
        val buildConfigFile = layout.buildDirectory.file(
            "generated/source/buildConfig/release/com/chartboost/prebid/BuildConfig.java",
        )
        inputs.files(pomFile, buildConfigFile)
        doLast {
            // Anchor on this artifact's own <artifactId> so the match is its project <version>, not a
            // dependency's, should the POM ever gain a <dependencies> block.
            val pomVersion = Regex("<artifactId>$publishedArtifactId</artifactId>\\s*<version>(.+?)</version>")
                .find(pomFile.get().asFile.readText())?.groupValues?.get(1)
                ?: error("could not read the published version from ${pomFile.get().asFile.path}")
            val embedded = Regex("""ADAPTER_VERSION = "(.+?)"""").find(buildConfigFile.get().asFile.readText())
                ?.groupValues?.get(1)
                ?: error("could not read ADAPTER_VERSION from ${buildConfigFile.get().asFile.path}")
            require(pomVersion == embedded) {
                "version drift: Maven coordinate is '$pomVersion' but BuildConfig.ADAPTER_VERSION is '$embedded'"
            }
            logger.lifecycle("verifyPublishedVersion: coordinate and embedded version agree ($pomVersion)")
        }
    }

    // Guards against a hand-run publish landing a release-candidate build on the public repo: the
    // -PprebidArtifactoryRepoKey override beats the CHARTBOOST_PREBID_IS_RELEASE flag (see above), so a
    // fat-fingered invocation could otherwise point an RC version at the public "chartboost-ads" repo.
    val verifyPublicReleaseVersion = tasks.register("verifyPublicReleaseVersion") {
        doLast {
            val isRcVersion = adapterVersion.contains("-rc")
            require(!(artifactoryRepoKey == publicRepoKey && isRcVersion)) {
                "Refusing to publish RC version '$adapterVersion' to the public repo '$publicRepoKey'. " +
                    "RCs are private-only; drop the -PprebidArtifactoryRepoKey override or publish a " +
                    "non-RC version."
            }
        }
    }

    tasks.named<org.jfrog.gradle.plugin.artifactory.task.ArtifactoryTask>("artifactoryPublish") {
        publications(publishing.publications.getByName("release"))
        // ArtifactoryTask is not an AbstractPublishToMaven, so the configureEach below doesn't reach it.
        // The publication points at a fixed AAR path, so wire the build explicitly or a stale/missing AAR
        // gets published. verifyPublishedVersion gates the publish on coordinate/binary version agreement;
        // verifyPublicReleaseVersion gates it on never landing an RC on the public repo.
        dependsOn("assembleRelease", verifyPublishedVersion, verifyPublicReleaseVersion)
        // Surface the destination in the build log: this task can target a public repo, so an accidental
        // public publish should be obvious in CI output rather than silent.
        doFirst {
            // Derive the label from the resolved repo key (not the flag) so it stays honest even when the
            // -PprebidArtifactoryRepoKey override picks the destination.
            val visibility = if (artifactoryRepoKey == publicRepoKey) "PUBLIC" else "private"
            logger.lifecycle("Publishing chartboost-prebid-adapter:$adapterVersion to $visibility repo '$artifactoryRepoKey'")
        }
    }

    // The publication points at the built AAR, so the maven-publish tasks (e.g. publishToMavenLocal) need
    // it built first.
    tasks.withType<AbstractPublishToMaven>().configureEach { dependsOn("assembleRelease") }
}
