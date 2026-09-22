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

    // DriverErrorBanner + the retry/localized() extensions this screen reuses
    // for the inline accept/decline failure banner are wired in automatically
    // by the feature convention plugin.

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
