plugins {
    alias(libs.plugins.driver.android.library)
    alias(libs.plugins.driver.android.hilt)
}

android {
    namespace = "app.qrmenu.driver.datastore"
}

dependencies {
    api(project(":core:common"))
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.appcompat) // AppCompatDelegate.setApplicationLocales

    testImplementation(libs.junit)
}
