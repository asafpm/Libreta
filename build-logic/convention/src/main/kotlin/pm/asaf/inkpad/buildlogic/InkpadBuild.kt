package pm.asaf.inkpad.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/** Build settings shared by every module. */
object InkpadBuild {
    const val NAMESPACE_ROOT = "pm.asaf.inkpad"
    const val COMPILE_SDK = 37
    const val TARGET_SDK = 37
    const val MIN_SDK = 29

    val javaVersion: JavaVersion = JavaVersion.VERSION_17
    val jvmTarget: JvmTarget = JvmTarget.JVM_17
}

/** `:core:model` -> `pm.asaf.inkpad.core.model` */
internal fun Project.inkpadNamespace(): String = InkpadBuild.NAMESPACE_ROOT + path.replace(':', '.')
