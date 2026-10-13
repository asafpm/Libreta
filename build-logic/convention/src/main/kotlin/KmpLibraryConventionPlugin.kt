import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import pm.asaf.inkpad.buildlogic.InkpadBuild
import pm.asaf.inkpad.buildlogic.inkpadNamespace

/**
 * Shared (KMP) module: Android + JVM targets. The JVM target exists only for fast
 * unit tests; commonTest runs on both the JVM and the Android host.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.kotlin.multiplatform.library")

            extensions.configure<KotlinMultiplatformExtension> {
                jvm {
                    compilerOptions { jvmTarget.set(InkpadBuild.jvmTarget) }
                }
                targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach {
                    namespace = inkpadNamespace()
                    compileSdk = InkpadBuild.COMPILE_SDK
                    minSdk = InkpadBuild.MIN_SDK
                    compilerOptions { jvmTarget.set(InkpadBuild.jvmTarget) }
                    withHostTest { }
                }
                sourceSets.getByName("commonTest").dependencies {
                    implementation(kotlin("test"))
                }
            }

            // Kotlin and Java must agree on the JVM target; without a toolchain Java
            // would default to the daemon's JDK.
            plugins.withType<JavaBasePlugin> {
                extensions.configure<JavaPluginExtension> {
                    sourceCompatibility = InkpadBuild.javaVersion
                    targetCompatibility = InkpadBuild.javaVersion
                }
            }
        }
    }
}
