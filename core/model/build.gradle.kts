// SPDX-License-Identifier: GPL-2.0-or-later

plugins {
    alias(libs.plugins.libreta.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // ByteString: immutable encoded image/PDF bytes with content equality.
            api(libs.okio)
        }
    }
}
