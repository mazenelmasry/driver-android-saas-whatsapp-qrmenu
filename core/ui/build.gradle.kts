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

    // The platform logo in the screen header arrives from the admin panel over
    // the network, and both platforms' logos are SVG — the same three artifacts
    // `:feature:auth` already uses for the sign-in mark.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.svg)

    testImplementation(libs.junit)
}
