@file:Suppress("UnstableApiUsage")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "qrmenu-driver"

include(":app")

// Core modules — added as each is scaffolded (CLAUDE.md § بنية المشروع).
include(":core:common")
// include(":core:model")
include(":core:designsystem")
// include(":core:ui")
include(":core:network")
// include(":core:database")
include(":core:datastore")
// include(":core:location")
// include(":core:sync")
// include(":core:notifications")
// include(":core:updater")

// Feature modules
// include(":feature:auth")
include(":feature:onboarding")
// include(":feature:availability")
// include(":feature:trip")
// include(":feature:ledger")
// include(":feature:history")
// include(":feature:settings")
