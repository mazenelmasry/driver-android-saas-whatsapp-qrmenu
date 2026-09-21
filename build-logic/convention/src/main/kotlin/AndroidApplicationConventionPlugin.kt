import com.android.build.api.dsl.ApplicationExtension
import app.qrmenu.driver.configureFlavors
import app.qrmenu.driver.configureKotlinAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("org.jetbrains.kotlin.android")
            }

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = 35
                // NEW-B-01 — each brand gets its own applicationId, so a
                // rebranded device cannot inherit the previous tenant's token,
                // ZATCA key or order database. Application module only.
                configureFlavors(this, applyApplicationIdSuffix = true)
            }
        }
    }
}
