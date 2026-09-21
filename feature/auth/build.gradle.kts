plugins {
    alias(libs.plugins.driver.android.feature)
}

android {
    namespace = "app.qrmenu.driver.auth"
}

dependencies {
    implementation(project(":core:datastore"))
    implementation(project(":core:network"))

    // Dial codes and validation per region. Used instead of a hand-kept table so
    // the app cannot disagree with the backend, which canonicalises with the
    // same library (giggsey/libphonenumber-for-php).
    implementation(libs.libphonenumber)

    // The platform logo is a URL from the admin panel, and on both deployments
    // today it is an SVG — hence the svg decoder alongside the loader.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.svg)

    implementation(libs.compose.material.icons.extended)

    // Firebase Phone Auth (decision 17: SMS the first time / on a forgotten
    // password). `:app` also depends on the auth SDK for its own BuildConfig
    // flag, but that flag lives on `:app`'s BuildConfig and cannot be read
    // from here — this module needs its own dependency to call the SDK
    // directly. Versions come from the BOM so this can never drift from
    // `:app`'s copy.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)

    testImplementation(libs.junit)
}
