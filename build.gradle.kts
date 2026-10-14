// SPDX-License-Identifier: GPL-2.0-or-later

// Declaring every plugin here (apply false) puts them on one classloader, so all
// modules share the same KGP version instead of the one AGP pulls in transitively.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}
