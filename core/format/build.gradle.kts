// SPDX-License-Identifier: GPL-2.0-or-later

plugins {
    alias(libs.plugins.inkpad.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(libs.okio)
            implementation(libs.xmlutil.core)
        }
    }
}

// Tests read the sample documents in <repo>/testdata; the path reaches common test
// code through a system property (see TestData.kt in jvmTest/androidHostTest).
val testData: Directory = layout.settingsDirectory.dir("testdata")
tasks.withType<Test>().configureEach {
    systemProperty("inkpad.testdata", testData.asFile.absolutePath)
    inputs
        .dir(testData)
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("testdata")
}
