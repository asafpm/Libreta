// SPDX-License-Identifier: GPL-2.0-or-later

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
