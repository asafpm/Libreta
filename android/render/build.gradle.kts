// SPDX-License-Identifier: GPL-2.0-or-later

plugins {
    alias(libs.plugins.inkpad.android.library)
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:geometry"))
}
