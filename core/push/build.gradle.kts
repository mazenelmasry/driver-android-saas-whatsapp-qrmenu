plugins {
    alias(libs.plugins.driver.android.library)
    alias(libs.plugins.driver.android.hilt)
}

android {
    namespace = "app.qrmenu.driver.push"
}

dependencies {
    api(project(":core:common"))

    // The transport. Deliberately the ONLY module that knows Firebase
    // Messaging exists — every other module talks to `PushHandler`, so a
    // brand with no Firebase project (taaj) loses the push arm and nothing
    // else. Version from the BOM, same as :app.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
