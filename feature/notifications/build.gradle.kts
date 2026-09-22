plugins {
    alias(libs.plugins.driver.android.feature)
}

android {
    namespace = "app.qrmenu.driver.notifications"
}

dependencies {
    // For NotificationHistoryStore — the list's data source and the source
    // of the ViewModel's unread-count exposure.
    implementation(project(":core:database"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
