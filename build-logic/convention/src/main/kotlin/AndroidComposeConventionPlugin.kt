import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import pm.asaf.inkpad.buildlogic.lib
import pm.asaf.inkpad.buildlogic.libs

/** Enables Compose on an Android module; apply after the application or library convention. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            pluginManager.withPlugin("com.android.application") {
                extensions.configure<ApplicationExtension> { buildFeatures.compose = true }
            }
            pluginManager.withPlugin("com.android.library") {
                extensions.configure<LibraryExtension> { buildFeatures.compose = true }
            }

            dependencies {
                val bom = platform(libs.lib("compose-bom"))
                add("implementation", bom)
                add("testImplementation", bom)
                add("androidTestImplementation", bom)
                add("implementation", libs.lib("compose-ui"))
                add("implementation", libs.lib("compose-material3"))
                add("implementation", libs.lib("compose-ui-tooling-preview"))
                add("debugImplementation", libs.lib("compose-ui-tooling"))
                add("debugImplementation", libs.lib("compose-ui-test-manifest"))
                add("testImplementation", libs.lib("compose-ui-test-junit4"))
                add("androidTestImplementation", libs.lib("compose-ui-test-junit4"))
            }
        }
    }
}
