plugins {
    alias(libs.plugins.driver.android.library)
    alias(libs.plugins.driver.android.library.compose)
    alias(libs.plugins.driver.android.hilt)
}

android {
    namespace = "app.qrmenu.driver.location"
}

dependencies {
    api(project(":core:common"))
    api(project(":core:network"))
    implementation(project(":core:datastore"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))

    implementation(libs.play.services.location)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // The foreground-service notification + rationale/battery-guide screens are
    // Compose, same as every other module here — this is not a headless module.
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
