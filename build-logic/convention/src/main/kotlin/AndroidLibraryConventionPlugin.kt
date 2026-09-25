import com.android.build.api.dsl.LibraryExtension
import app.qrmenu.driver.configureFlavors
import app.qrmenu.driver.configureKotlinAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.android")
            }

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = extensions.getByType<VersionCatalogsExtension>().named("libs").findVersion("targetSdk").get().requiredVersion.toInt()
                testOptions.targetSdk = extensions.getByType<VersionCatalogsExtension>().named("libs").findVersion("targetSdk").get().requiredVersion.toInt()
                configureFlavors(this)
            }
        }
    }
}
