---
name: add-feature-module
description: Use this skill when adding a brand-new feature module under `:feature:<name>` in the driver Android project. Triggers — "add a feature module", "create a new feature", "scaffold a feature for X". Produces a buildable, type-safe-routed, Hilt-ready Compose feature module using this project's `driver.android.*` plugin ids and `app.qrmenu.driver` package, following the module list in the driver CLAUDE.md (auth, availability, trip, ledger, history, settings, onboarding).
---

# Skill: add-feature-module

Create a new `:feature:<name>` module that compiles on first try and matches every other feature in this repo.

## Before you start

1. Read CLAUDE.md's "🧱 بنية المشروع والمكتبات" section — the feature list is fixed for phase 1: `auth, availability, trip, ledger, history, settings, onboarding`. Confirm the feature isn't already one of these before scaffolding a new module; most new screens belong **inside** an existing feature module (use `add-compose-screen` instead), not in a brand-new one.
2. Confirm against CLAUDE.md's phase-1 week-by-week plan (§🗺️ المراحل) whether this module is scheduled now or is scope creep for a later phase.
3. This repo has **no separate `SPRINT_PROMPTS.md`** — unlike POS, all status tracking lives in CLAUDE.md's own "🔚 تسليم الجلسة" header and "🗺️ المراحل" table. Don't create a second tracking file.

## Steps

### 1. Create the directory layout

```
feature/<name>/
├─ build.gradle.kts
└─ src/main/
   ├─ AndroidManifest.xml          (empty <manifest /> tag)
   └─ java/app/qrmenu/driver/feature/<name>/
      └─ <Name>Navigation.kt
```

### 2. `build.gradle.kts` template

```kotlin
plugins {
    alias(libs.plugins.driver.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "app.qrmenu.driver.feature.<name>" }

dependencies {
    // Add ONLY core modules this feature actually needs:
    // implementation(project(":core:database"))
    // implementation(project(":core:network"))
    // implementation(project(":core:location"))
}
```

The `driver.android.feature` convention plugin (copied from POS's `pos.android.feature` and renamed) already wires: library + compose + hilt + designsystem + ui + common + model + navigation + viewmodel-compose. Do **not** re-declare those.

### 3. `AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest />
```

### 4. `<Name>Navigation.kt` template

```kotlin
package app.qrmenu.driver.feature.<name>

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable data object <Name>Route

fun NavGraphBuilder.<name>Graph() {
    composable<<Name>Route> { <Name>Screen() }
}

@Composable
fun <Name>Screen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("<Name> (Phase 1)")
    }
}
```

### 5. Wire into the project

- Add `include(":feature:<name>")` in `settings.gradle.kts`.
- Add `implementation(project(":feature:<name>"))` in `app/build.gradle.kts`.
- Call `<name>Graph()` from `app/.../navigation/DriverNavHost.kt`.

### 6. Update CLAUDE.md (BLOCKING — same change set)

- "🧱 بنية المشروع والمكتبات" module tree — add the module if it's new (it normally shouldn't be, per step 1).
- "🗺️ خريطة الشاشات" — add the route(s) this module introduces.
- Session-end handoff header — update "أين وصلنا الآن" per the file's own end-of-session protocol.

## Verification

```bash
./gradlew :feature:<name>:assembleMeniuraDebug
./gradlew :app:assembleMeniuraDebug :app:assembleTaajDebug
```

Both flavors must pass — this repo ships two flavors from day one, unlike a single-brand app.

## Anti-patterns

- ❌ Scaffolding a new feature module for a screen that belongs inside `trip`, `availability`, `ledger`, `history`, `settings`, `auth`, or `onboarding`.
- ❌ Re-applying `com.android.library` directly — use `driver.android.feature`.
- ❌ Copying POS's package prefix (`app.qrmenu.pos.feature.*`) instead of `app.qrmenu.driver.feature.*`.
- ❌ Declaring Hilt deps manually — the convention plugin includes them.
- ❌ Adding `:core:*` dependencies you don't actually use.
- ❌ Forgetting to update CLAUDE.md, or creating a second tracking file that doesn't exist in this repo's conventions.
- ❌ String-based nav routes — always `@Serializable` objects.
