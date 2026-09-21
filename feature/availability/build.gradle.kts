plugins {
    alias(libs.plugins.driver.android.feature)
}

android {
    namespace = "app.qrmenu.driver.availability"
}

dependencies {
    // AvailabilityApi + AuthApi (for the initial `/driver/me` read) and every
    // DTO this screen renders (DriverDto, AvailabilityResponse,
    // AvailabilityContextDto) already live here — nothing new to declare.
    implementation(project(":core:network"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
