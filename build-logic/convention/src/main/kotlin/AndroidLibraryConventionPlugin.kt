import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.kotlin
import pm.asaf.inkpad.buildlogic.InkpadBuild
import pm.asaf.inkpad.buildlogic.inkpadNamespace
import pm.asaf.inkpad.buildlogic.lib
import pm.asaf.inkpad.buildlogic.libs

/** Android-only library (AGP built-in Kotlin) with Robolectric unit tests. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply(LintConventionPlugin::class.java)

            extensions.configure<LibraryExtension> {
                namespace = inkpadNamespace()
                compileSdk = InkpadBuild.COMPILE_SDK
                defaultConfig {
                    minSdk = InkpadBuild.MIN_SDK
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    sourceCompatibility = InkpadBuild.javaVersion
                    targetCompatibility = InkpadBuild.javaVersion
                }
                testOptions.unitTests.isIncludeAndroidResources = true
                testOptions.unitTests.all { it.jvmArgs(InkpadBuild.robolectricJvmArgs) }
            }

            dependencies {
                add("testImplementation", kotlin("test"))
                add("testImplementation", libs.lib("junit4"))
                add("testImplementation", libs.lib("robolectric"))
                add("testImplementation", libs.lib("androidx-test-ext-junit"))
            }
        }
    }
}
