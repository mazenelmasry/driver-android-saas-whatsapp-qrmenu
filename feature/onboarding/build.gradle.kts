plugins {
    alias(libs.plugins.driver.android.feature)
}

android {
    namespace = "app.qrmenu.driver.onboarding"
}

dependencies {
    // LocaleManager lives here: the picker's whole job is to write the choice
    // and apply it.
    implementation(project(":core:datastore"))

    testImplementation(libs.junit)
}
