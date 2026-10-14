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

    // Device tests save reports with TestStorage.
    androidTestImplementation(libs.androidx.test.services.storage)
    androidTestUtil(libs.androidx.test.services)
}

android {
    defaultConfig {
        // Device test reports are pulled into build/outputs/connected_android_test_additional_output.
        testInstrumentationRunnerArguments["useTestStorageService"] = "true"
    }
    buildTypes {
        // Release settings with the debug key, to measure speed on a device as users get it
        // (debuggable builds run much slower):
        //   ./gradlew :android:app:connectedBenchmarkAndroidTest -Pinkpad.testBuildType=benchmark
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    testBuildType = providers.gradleProperty("inkpad.testBuildType").getOrElse("debug")
}

dependencies {
    // Device tests host views in ComponentActivity, declared by this manifest (debug has it already).
    "benchmarkImplementation"(libs.compose.ui.test.manifest)
}

val testData: Directory = layout.settingsDirectory.dir("testdata")

// The sample documents, readable by device tests as assets.
androidComponents {
    onVariants { variant ->
        variant.androidTest
            ?.sources
            ?.assets
            ?.addStaticSourceDirectory(testData.asFile.absolutePath)
    }
}

tasks.withType<Test>().configureEach {
    // Robolectric native graphics needs more than the default 512 MB on SDK 35+.
    maxHeapSize = "3g"
    systemProperty("inkpad.testdata", testData.asFile.absolutePath)
    inputs
        .dir(testData)
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("testdata")
}
