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
        // The self-updater compares this INTEGER, never the name string. Both
        // come from app.qrmenu.driver.DriverVersion in build-logic, which also
        // feeds BuildConfig.APP_VERSION_CODE — the number the CLIENT reports to
        // the server must be the same number the installer stamps.
        versionCode = app.qrmenu.driver.DriverVersion.CODE
        versionName = app.qrmenu.driver.DriverVersion.NAME

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

// ── Firebase, one project per BRAND ──────────────────────────────────────
//
// The two brands belong to DIFFERENT Google accounts, so they cannot share a
// Firebase project. Each brand's google-services.json therefore lives in its
// own flavour source set (app/src/<flavour>/), never at app/ where one file
// would silently serve both. The file is gitignored (it carries an API key and
// a project id) — download it from that brand's console.
//
// A brand whose project does not exist yet must still BUILD. The plugin is
// project-wide, so it cannot be applied per flavour; instead the
// process<Variant>GoogleServices task is disabled for a flavour with no file,
// and that flavour is stamped FIREBASE_CONFIGURED = false. Nothing then
// pretends phone verification works there: the app reads the flag and says so,
// rather than crashing on a null FirebaseApp at the moment a driver taps
// "send code".
val brandsWithFirebase = listOf("meniura", "taaj")
    .filter { file("src/$it/google-services.json").exists() }

if (brandsWithFirebase.isNotEmpty()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)

    // A flavour without a file would fail processGoogleServices and take the
    // whole build down with it — including verify.sh, which builds both.
    tasks.matching {
        it.name.startsWith("process") && it.name.endsWith("GoogleServices")
    }.configureEach {
        val variant = name.removePrefix("process").removeSuffix("GoogleServices")
        enabled = brandsWithFirebase.any { brand ->
            variant.startsWith(brand, ignoreCase = true)
        }
    }
}

android {
    productFlavors {
        listOf("meniura", "taaj").forEach { brand ->
            getByName(brand) {
                buildConfigField(
                    "boolean",
                    "FIREBASE_CONFIGURED",
                    brandsWithFirebase.contains(brand).toString(),
                )
            }
        }
    }
}

dependencies {
    // Firebase: phone sign-in (decision 17) and the high-priority data
    // messages that wake the app for an offer (decision 13). Versions come
    // from the BOM so the two can never drift apart. OneSignal stays the
    // restaurant/customer channel and is deliberately not used here.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(project(":core:designsystem"))
    // The shell hands the app-level alert banners to whichever screen scaffold
    // is on show, through `LocalAppBanners` — see `SignedInScreen`.
    implementation(project(":core:ui"))
    implementation(project(":core:push"))
    implementation(project(":core:datastore"))
    // Included so the Hilt graph is actually VALIDATED in a component: a module
    // that only compiles on its own proves nothing about whether its bindings
    // resolve. No screen consumes it yet.
    implementation(project(":core:network"))
    implementation(project(":core:common"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:availability"))
    implementation(project(":feature:orders"))
    implementation(project(":feature:trip"))
    implementation(project(":feature:notifications"))
    implementation(project(":feature:account"))
    implementation(project(":feature:wallet"))
    implementation(project(":feature:updater"))
    // :app now hosts composables that resolve their own ViewModels (the tab
    // scaffold). Feature modules get this from the convention plugin; :app is
    // an application module and does not.
    implementation(libs.hilt.navigation.compose)
    // The foreground service lives here. :app must carry it on the classpath
    // or the manifest's <service> entry resolves to nothing at merge time and
    // Hilt never generates its component.
    implementation(project(":core:location"))
    implementation(project(":core:notifications"))

    // The outbox WORKER lives in :feature:trip; :app only hands WorkManager
    // the Hilt factory (DriverApplication implements Configuration.Provider),
    // and needs these two on its own classpath to name those types —
    // :feature:trip declares them `implementation`, so they do not leak here.
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)

    // The unsent-actions banner reads the outbox's pending count directly —
    // it is the only surface that tells a driver a delivery has not reached
    // the restaurant yet.
    implementation(project(":core:database"))
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
