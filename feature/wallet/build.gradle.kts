plugins {
    alias(libs.plugins.driver.android.feature)
}

android {
    namespace = "app.qrmenu.driver.wallet"
}

dependencies {
    // LedgerApi + its DTOs (LedgerSummaryDto/LedgerEntryDto/SettlementDto) and
    // AuthApi.me() (for the RestaurantLink list the switcher renders) already
    // live here — nothing new to declare.
    implementation(project(":core:network"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
