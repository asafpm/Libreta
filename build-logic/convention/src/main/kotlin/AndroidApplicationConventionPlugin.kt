// SPDX-License-Identifier: GPL-2.0-or-later

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.kotlin
import pm.asaf.libreta.buildlogic.LibretaBuild
import pm.asaf.libreta.buildlogic.lib
import pm.asaf.libreta.buildlogic.libs

/** The Android app module (AGP built-in Kotlin) with Robolectric unit tests. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply(LintConventionPlugin::class.java)

            extensions.configure<ApplicationExtension> {
                namespace = LibretaBuild.NAMESPACE_ROOT
                compileSdk = LibretaBuild.COMPILE_SDK
                defaultConfig {
                    applicationId = LibretaBuild.NAMESPACE_ROOT
                    minSdk = LibretaBuild.MIN_SDK
                    targetSdk = LibretaBuild.TARGET_SDK
                    versionCode = 1
                    versionName = "0.1.0"
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    sourceCompatibility = LibretaBuild.javaVersion
                    targetCompatibility = LibretaBuild.javaVersion
                }
                testOptions.unitTests.isIncludeAndroidResources = true
                testOptions.unitTests.all { it.jvmArgs(LibretaBuild.robolectricJvmArgs) }
            }

            dependencies {
                add("testImplementation", kotlin("test"))
                add("testImplementation", libs.lib("junit4"))
                add("testImplementation", libs.lib("robolectric"))
                add("testImplementation", libs.lib("androidx-test-ext-junit"))
                add("androidTestImplementation", libs.lib("androidx-test-ext-junit"))
                add("androidTestImplementation", libs.lib("androidx-test-espresso-core"))
            }
        }
    }
}
