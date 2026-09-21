plugins {
    alias(libs.plugins.driver.android.library)
    alias(libs.plugins.driver.android.library.compose)
}

android {
    namespace = "app.qrmenu.driver.designsystem"
}

dependencies {
    api(libs.compose.ui)
    api(libs.compose.material3)
    api(libs.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
}
