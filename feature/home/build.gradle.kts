plugins {
    alias(libs.plugins.driver.android.feature)
}

android {
    namespace = "app.qrmenu.driver.home"
}

dependencies {
    // TokenStore, to clear the session on sign-out even when the server call fails.
    implementation(project(":core:datastore"))
    implementation(project(":core:network"))

    // A restaurant's logo is a URL from the admin panel, and on both deployments
    // today it may be an SVG — same reason :feature:auth carries the svg decoder
    // for the platform mark.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.svg)

    // The three link statuses (invited/active/suspended) must look different,
    // not just read differently — see RestaurantCard. The distinguishing icons
    // (check circle / schedule / block) are outside material3's small bundled
    // core set, hence the extended pack (same as :feature:auth).
    implementation(libs.compose.material.icons.extended)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
