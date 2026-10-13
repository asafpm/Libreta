plugins {
    `kotlin-dsl`
}

group = "pm.asaf.inkpad.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = libs.plugins.inkpad.kmp.library.get().pluginId
            implementationClass = "KmpLibraryConventionPlugin"
        }
    }
}
