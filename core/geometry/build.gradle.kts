plugins {
    alias(libs.plugins.inkpad.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
        }
    }
}
