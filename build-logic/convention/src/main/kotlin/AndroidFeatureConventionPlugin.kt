import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Convention plugin for feature modules.
 * Applies library + compose + hilt and wires shared core modules.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("driver.android.library")
            pluginManager.apply("driver.android.library.compose")
            pluginManager.apply("driver.android.hilt")

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            dependencies {
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:common"))
                // :core:ui and :core:model are wired in only once they exist.
                // Creating them empty just to satisfy this list would put two
                // placeholder modules in the build with nothing to put in them —
                // :core:ui earns its keep when the first screen that LOADS data
                // needs the shared skeleton/error/offline components, and
                // :core:model when there is a domain type to share. Until then a
                // feature module simply does not depend on them.
                listOf(":core:ui", ":core:model")
                    .filter { rootProject.findProject(it) != null }
                    .forEach { add("implementation", project(it)) }

                add("implementation", libs.findLibrary("androidx.lifecycle.runtime.compose").get())
                add("implementation", libs.findLibrary("androidx.lifecycle.viewmodel.compose").get())
                add("implementation", libs.findLibrary("androidx.navigation.compose").get())
                add("implementation", libs.findLibrary("hilt.navigation.compose").get())
                add("implementation", libs.findLibrary("compose.material3").get())
                add("implementation", libs.findLibrary("kotlinx.coroutines.android").get())
            }
        }
    }
}
