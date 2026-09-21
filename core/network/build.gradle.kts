plugins {
    alias(libs.plugins.driver.android.library)
    alias(libs.plugins.driver.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.qrmenu.driver.network"

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        // API_BASE_URL / APP_VERSION_* come from the brand flavours wired in
        // build-logic (configureFlavors): release uses the flavour's production
        // domain, debug is overridden to the local dev URL
        // (-Pdriver.debugBaseUrl / local.properties). Staging inherits debug,
        // including its API_BASE_URL, via initWith.
        create("staging") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
        }
    }
}

dependencies {
    api(project(":core:common"))
    api(project(":core:datastore"))

    api(libs.retrofit.core)
    api(libs.retrofit.kotlinx.serialization)
    api(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    // TokenStore / AppLocaleStore are final classes that build
    // EncryptedSharedPreferences off a Context in their constructors — there is
    // nothing to hand-fake in a JVM unit test. Same reason, same choice as the
    // POS repo.
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
