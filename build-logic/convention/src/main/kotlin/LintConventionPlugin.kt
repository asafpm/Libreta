import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import pm.asaf.inkpad.buildlogic.libs

/** ktlint + detekt for every module; applied by the other convention plugins. */
class LintConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")
            pluginManager.apply("dev.detekt")

            extensions.configure<KtlintExtension> {
                version.set(libs.findVersion("ktlint").get().requiredVersion)
            }

            extensions.configure<DetektExtension> {
                buildUponDefaultConfig.set(true)
                config.setFrom(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))
                // Cover every source set (KMP commonMain/jvmTest/…, Android main/test/androidTest).
                source.setFrom("src")
                parallel.set(true)
            }
        }
    }
}
