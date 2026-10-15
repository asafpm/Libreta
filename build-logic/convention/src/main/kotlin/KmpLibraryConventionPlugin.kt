// SPDX-License-Identifier: GPL-2.0-or-later

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import pm.asaf.libreta.buildlogic.CommonPurityCheck
import pm.asaf.libreta.buildlogic.LibretaBuild
import pm.asaf.libreta.buildlogic.libretaNamespace

/**
 * Shared (KMP) module: Android + JVM targets. The JVM target exists only for fast
 * unit tests; commonTest runs on both the JVM and the Android host.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.kotlin.multiplatform.library")
            pluginManager.apply(LintConventionPlugin::class.java)

            extensions.configure<KotlinMultiplatformExtension> {
                jvm {
                    compilerOptions { jvmTarget.set(LibretaBuild.jvmTarget) }
                }
                targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach {
                    namespace = libretaNamespace()
                    compileSdk = LibretaBuild.COMPILE_SDK
                    minSdk = LibretaBuild.MIN_SDK
                    compilerOptions { jvmTarget.set(LibretaBuild.jvmTarget) }
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
                    sourceCompatibility = LibretaBuild.javaVersion
                    targetCompatibility = LibretaBuild.javaVersion
                }
            }

            val purity =
                tasks.register<CommonPurityCheck>("checkCommonPurity") {
                    group = "verification"
                    description = "Fails on java/javax/android imports in common source sets."
                    sources.from("src/commonMain/kotlin", "src/commonTest/kotlin")
                    marker.set(layout.buildDirectory.file("commonPurity/ok"))
                }
            tasks.named("check") { dependsOn(purity) }
        }
    }
}
