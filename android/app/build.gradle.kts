// SPDX-License-Identifier: GPL-2.0-or-later

plugins {
    alias(libs.plugins.inkpad.android.application)
    alias(libs.plugins.inkpad.android.compose)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:format"))
    implementation(project(":core:geometry"))
    implementation(project(":android:render"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.compose.material.icons.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.kotlinx.coroutines.test)
}

val testData: Directory = layout.settingsDirectory.dir("testdata")

tasks.withType<Test>().configureEach {
    // Robolectric native graphics needs more than the default 512 MB on SDK 35+.
    maxHeapSize = "3g"
    systemProperty("inkpad.testdata", testData.asFile.absolutePath)
    inputs
        .dir(testData)
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("testdata")
}
