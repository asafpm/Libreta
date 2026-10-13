plugins {
    `kotlin-dsl`
}

group = "pm.asaf.inkpad.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ktlint.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = libs.plugins.inkpad.kmp.library.get().pluginId
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.inkpad.android.library.get().pluginId
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = libs.plugins.inkpad.android.application.get().pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidCompose") {
            id = libs.plugins.inkpad.android.compose.get().pluginId
            implementationClass = "AndroidComposeConventionPlugin"
        }
    }
}
