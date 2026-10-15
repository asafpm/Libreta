// SPDX-License-Identifier: GPL-2.0-or-later

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.kotlin
import pm.asaf.libreta.buildlogic.LibretaBuild
import pm.asaf.libreta.buildlogic.libretaNamespace
import pm.asaf.libreta.buildlogic.lib
import pm.asaf.libreta.buildlogic.libs

/** Android-only library (AGP built-in Kotlin) with Robolectric unit tests. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply(LintConventionPlugin::class.java)

            extensions.configure<LibraryExtension> {
                namespace = libretaNamespace()
                compileSdk = LibretaBuild.COMPILE_SDK
                defaultConfig {
                    minSdk = LibretaBuild.MIN_SDK
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
                add("androidTestImplementation", kotlin("test"))
                add("androidTestImplementation", libs.lib("androidx-test-ext-junit"))
                add("androidTestImplementation", libs.lib("androidx-test-runner"))
            }
        }
    }
}
