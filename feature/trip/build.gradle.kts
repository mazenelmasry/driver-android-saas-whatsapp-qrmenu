plugins {
    alias(libs.plugins.driver.android.feature)
}

android {
    namespace = "app.qrmenu.driver.trip"
}

dependencies {
    // OrderApi + every DTO the offer screen renders (DriverOrderDto,
    // OfferDto, DriverIssueCode's neighbours in OrderDtos.kt) already live
    // here — nothing new to declare.
    implementation(project(":core:network"))

    // The offline outbox for trip commands (picked-up / delivered / issue) —
    // see TripRepository's own doc for why a command is queued before the
    // network call is even attempted.
    implementation(project(":core:database"))

    // DriverTripActivityState — TripViewModel is the one place that knows
    // when this driver's trip moves past pickup and when it ends, which is
    // exactly what gates :core:location's breadcrumb collection (see that
    // class's own doc).
    implementation(project(":core:location"))

    // Background drain of that outbox. The module that OWNS the queue owns
    // its draining — :app only registers the Hilt worker factory, it does not
    // know what a trip command is.
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // DriverErrorBanner + the retry/localized() extensions this screen reuses
    // for the inline accept/decline failure banner are wired in automatically
    // by the feature convention plugin.

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
