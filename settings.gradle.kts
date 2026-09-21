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
include(":core:ui")
include(":core:network")
// include(":core:database")
include(":core:datastore")
include(":core:location")
// include(":core:sync")
// include(":core:notifications")
// include(":core:updater")

// Feature modules
include(":feature:auth")
include(":feature:onboarding")
// :feature:home was replaced by :feature:account (2026-09-21) — its tab
// merged into «حسابى» rather than standing alone.
include(":feature:availability")
include(":feature:orders")
include(":feature:notifications")
include(":feature:account")
include(":feature:wallet")
// include(":feature:trip")
// include(":feature:ledger")
// include(":feature:history")
// include(":feature:settings")
