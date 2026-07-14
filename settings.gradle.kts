/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Chartboost Monetization SDK releases (not on Maven Central past 9.2.1).
        maven { url = uri("https://cboost.jfrog.io/artifactory/chartboost-ads") }
    }
}

rootProject.name = "chartboost-prebid"

include(":ChartboostPrebidPlugin")
