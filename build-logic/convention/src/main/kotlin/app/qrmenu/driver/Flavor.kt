package app.qrmenu.driver

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import java.util.Properties

@Suppress("EnumEntryName")
enum class DriverFlavorDimension {
    brand,
}

/**
 * Brand flavors. Each multi-brand platform served by the same backend gets its own
 * production domain — no single domain is hardcoded.
 */
@Suppress("EnumEntryName")
enum class DriverFlavor(
    val dimension: DriverFlavorDimension,
    val releaseBaseUrl: String,
    /**
     * Appended to `applicationId` so each brand is a DISTINCT Android app.
     *
     * Android keys app storage on the application id. Shipping both brands under
     * one id would let a meniura install laid over a taaj one inherit the previous
     * tenant's EncryptedSharedPreferences driver token, its Room action queue and
     * its cached customer addresses — cross-tenant exposure on any rebranded
     * phone, and the two could never coexist on one device. The POS repo learned
     * this the expensive way (its finding NEW-B-01); we start correct instead.
     *
     * An application id is app IDENTITY: changing it later orphans every existing
     * install (no updates, no data migration). So it is frozen from the first
     * build, before any driver has the app.
     */
    val applicationIdSuffix: String,
) {
    taaj(DriverFlavorDimension.brand, "https://app.taaj.me", ".taaj"),
    meniura(DriverFlavorDimension.brand, "https://app.meniura.com", ".meniura"),
    ;

    /**
     * Google Play Data Safety URL for this brand's app listing. Served by the
     * SAME backend that [releaseBaseUrl] points to — `PrivacyPolicyController`
     * (backend `routes/web.php`) resolves the brand from the request host, so
     * this is just [releaseBaseUrl] + the fixed path, not a separately hosted
     * page. See CLAUDE.md §13 (2026-08-11).
     */
    val privacyPolicyUrl: String get() = "$releaseBaseUrl/privacy-policy"
}

/**
 * Resolves the debug/staging base URL from an untracked source so each developer
 * machine can point at its own LAN backend without editing tracked files.
 * Priority: -Pdriver.debugBaseUrl  >  local.properties (driver.debugBaseUrl)  >  emulator default.
 */
internal fun Project.resolveDebugBaseUrl(): String {
    val default = "http://10.0.2.2:8000"
    (findProperty("driver.debugBaseUrl") as? String)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val localProps = rootProject.file("local.properties")
    if (localProps.exists()) {
        val props = Properties().apply { localProps.inputStream().use { load(it) } }
        props.getProperty("driver.debugBaseUrl")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return default
}

/**
 * Registers the brand product flavors and wires API_BASE_URL / APP_VERSION_* into BuildConfig
 * for every Android module. Retrofit reads core:network's BuildConfig (NetworkModule).
 *  - release  -> the flavor's production domain
 *  - debug    -> a local dev URL from an untracked source (build type overrides the flavor value)
 *  - staging  -> inherits debug via initWith(getByName("debug")) in the module build script
 */
internal fun Project.configureFlavors(
    commonExtension: CommonExtension<*, *, *, *, *, *>,
    /**
     * Only the APPLICATION module may set an `applicationIdSuffix` — a library
     * has no applicationId, and AGP hard-fails configuration if one is set
     * there ("Library projects cannot set applicationIdSuffix"). A `this is
     * ApplicationProductFlavor` check is not enough: the shared DSL type
     * accepts the property and the failure surfaces later, at configuration of
     * the library module. So the caller states it explicitly.
     */
    applyApplicationIdSuffix: Boolean = false,
) {
    val debugBaseUrl = resolveDebugBaseUrl()
    commonExtension.apply {
        buildFeatures { buildConfig = true }
        flavorDimensions += DriverFlavorDimension.brand.name

        productFlavors {
            DriverFlavor.values().forEach { driverFlavor ->
                create(driverFlavor.name) {
                    dimension = driverFlavor.dimension.name
                    if (applyApplicationIdSuffix &&
                        this is com.android.build.api.dsl.ApplicationProductFlavor
                    ) {
                        applicationIdSuffix = driverFlavor.applicationIdSuffix
                    }
                    buildConfigField("String", "API_BASE_URL", "\"${driverFlavor.releaseBaseUrl}\"")
                    // The version is NOT flavour-specific: both brands ship the same
                    // build, and the updater compares the integer CODE (never the name
                    // string). See DriverVersion.
                    buildConfigField("int", "APP_VERSION_CODE", "${DriverVersion.CODE}")
                    buildConfigField("String", "APP_VERSION_NAME", "\"${DriverVersion.NAME}\"")
                    buildConfigField("String", "PRIVACY_POLICY_URL", "\"${driverFlavor.privacyPolicyUrl}\"")
                }
            }
        }

        // Build type wins over flavor for the same field: point non-prod builds at the local
        // server. configureEach also catches `staging`, which each module creates later via
        // initWith(debug) — so we never rely on initWith copying buildConfigFields.
        buildTypes {
            configureEach {
                if (name == "debug" || name == "staging") {
                    buildConfigField("String", "API_BASE_URL", "\"$debugBaseUrl\"")
                }
            }
        }
    }
}
