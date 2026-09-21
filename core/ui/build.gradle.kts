plugins {
    alias(libs.plugins.driver.android.library)
    alias(libs.plugins.driver.android.library.compose)
    alias(libs.plugins.driver.android.hilt)
}

android {
    namespace = "app.qrmenu.driver.ui"
}

dependencies {
    api(project(":core:designsystem"))
    api(project(":core:common"))
    // For DriverApiError/DriverErrorCode: the whole point of this module's error
    // layer is to turn a machine-readable code into a sentence in the driver's
    // language, so it has to know the code type.
    api(project(":core:network"))

    implementation(libs.compose.material3)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}
