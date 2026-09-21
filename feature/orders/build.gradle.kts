plugins {
    alias(libs.plugins.driver.android.feature)
}

android {
    namespace = "app.qrmenu.driver.orders"
}

dependencies {
    // OrderApi + every DTO this screen renders (DriverOrderDto,
    // AvailableOrdersResponse, MyOrdersResponse, AvailabilityContextDto) already
    // live here — nothing new to declare.
    implementation(project(":core:network"))

    // `NoOrdersReason` (decision 47's "why is this list empty" vocabulary)
    // lives in :core:ui, which the feature convention plugin already wires in
    // — the availability screen and this list render the SAME reason and must
    // never drift apart.

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
