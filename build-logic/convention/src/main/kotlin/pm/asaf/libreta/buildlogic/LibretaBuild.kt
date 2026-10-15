// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/** Build settings shared by every module. */
object LibretaBuild {
    const val NAMESPACE_ROOT = "pm.asaf.libreta"
    const val COMPILE_SDK = 37
    const val TARGET_SDK = 37
    const val MIN_SDK = 29

    val javaVersion: JavaVersion = JavaVersion.VERSION_17
    val jvmTarget: JvmTarget = JvmTarget.JVM_17

    /** Robolectric touches JDK internals that JDK 17+ no longer exports by default. */
    val robolectricJvmArgs: List<String> =
        listOf(
            "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
            "--enable-native-access=ALL-UNNAMED",
        )
}

/** `:core:model` -> `pm.asaf.libreta.core.model` */
internal fun Project.libretaNamespace(): String = LibretaBuild.NAMESPACE_ROOT + path.replace(':', '.')
