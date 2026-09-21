import java.util.Properties

plugins {
    alias(libs.plugins.driver.android.application)
    alias(libs.plugins.driver.android.application.compose)
    alias(libs.plugins.driver.android.hilt)
    alias(libs.plugins.kotlin.serialization)
    // google-services is applied only when a google-services.json is present, so
    // the project builds before Firebase is wired (see the apply block below).
}

android {
    namespace = "app.qrmenu.driver"

    defaultConfig {
        applicationId = "app.qrmenu.driver"
        // CLAUDE.md § ترقيم الإصدارات — versionCode = MAJOR*10000 + MINOR*100 + PATCH.
        // The self-updater compares this INTEGER, never the name string.
        versionCode = 10000
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        buildConfig = true
    }

    // Production locales + the pseudo-locales for QA on debug builds only.
    // Bengali (bn) and Hindi (hi) render through the system font — the bundled
    // IBM Plex Sans Arabic covers ar/en/ur only (CLAUDE.md § الخط).
    androidResources {
        localeFilters += listOf("ar", "en", "ur", "bn", "hi", "en-rXA", "ar-rXB")
    }

    // Production signing — credentials supplied OUT OF BAND, never committed:
    // ~/.gradle/gradle.properties or CI env as RELEASE_STORE_FILE /
    // RELEASE_STORE_PASSWORD / RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD.
    //   keytool -genkeypair -v -keystore upload.jks -keyalg RSA -keysize 2048 \
    //     -validity 10000 -alias upload
    val releaseStoreFile = (findProperty("RELEASE_STORE_FILE") as String?)
        ?: System.getenv("RELEASE_STORE_FILE")

    // A release build with no signing secrets otherwise falls back to the GLOBAL
    // debug key — anyone holding a debug.keystore could then sideload an "update"
    // over a real install. That is especially dangerous here because the app
    // self-updates from our own server. Refuse, unless a developer explicitly
    // opts in for a throwaway local build.
    val allowDebugSignedRelease =
        ((findProperty("driver.allowDebugSignedRelease") as? String)
            ?: run {
                val localProps = rootProject.file("local.properties")
                if (localProps.exists()) {
                    Properties().apply { localProps.inputStream().use { load(it) } }
                        .getProperty("driver.allowDebugSignedRelease")
                } else {
                    null
                }
            })?.toBoolean() == true
    val isBuildingRelease = gradle.startParameter.taskNames.any { taskName ->
        val leaf = taskName.substringAfterLast(':').lowercase()
        leaf.contains("release") &&
            (leaf.startsWith("assemble") || leaf.startsWith("bundle") ||
                leaf.startsWith("install") || leaf.startsWith("package"))
    }

    signingConfigs {
        create("release") {
            if (releaseStoreFile != null) {
                storeFile = file(releaseStoreFile)
                storePassword = (findProperty("RELEASE_STORE_PASSWORD") as String?)
                    ?: System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = (findProperty("RELEASE_KEY_ALIAS") as String?)
                    ?: System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = (findProperty("RELEASE_KEY_PASSWORD") as String?)
                    ?: System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // API_BASE_URL comes from the brand flavors (configureFlavors in build-logic).
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            isPseudoLocalesEnabled = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (releaseStoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                if (isBuildingRelease && !allowDebugSignedRelease) {
                    throw GradleException(
                        "Refusing to build a debug-signed RELEASE. Provide the upload key " +
                            "(RELEASE_STORE_FILE / RELEASE_STORE_PASSWORD / RELEASE_KEY_ALIAS / " +
                            "RELEASE_KEY_PASSWORD), or pass -Pdriver.allowDebugSignedRelease=true " +
                            "for a throwaway local build only.",
                    )
                }
                signingConfigs.getByName("debug")
            }
        }
        // Staging: a debug-like build pointed at a LAN backend from a real phone.
        // Its own applicationId so a debuggable build can never install over
        // production. Inherits debug's API_BASE_URL via initWith.
        create("staging") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".staging"
            isDebuggable = true
            matchingFallbacks += listOf("debug")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/*.kotlin_module"
        }
    }
}

// Apply google-services only once the file exists, so `assembleDebug` works
// before Firebase is provisioned (CLAUDE.md § ما يحتاجه صاحب المشروع، بند ٣).
if (file("google-services.json").exists() ||
    file("src/meniura/google-services.json").exists() ||
    file("src/taaj/google-services.json").exists()
) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
}
