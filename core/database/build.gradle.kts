plugins {
    alias(libs.plugins.driver.android.library)
    alias(libs.plugins.driver.android.hilt)
    alias(libs.plugins.driver.android.room)
}

android {
    namespace = "app.qrmenu.driver.database"

    // Robolectric DAO tests below need Android's real android.database.sqlite,
    // which the default JVM unit-test classpath does not provide.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// Exported schema JSON is how a future migration gets proven against a real
// prior-version database instead of "reads correctly" — see
// .claude/skills/add-room-entity §7. Committed under core/database/schemas/.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(project(":core:common"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
}
