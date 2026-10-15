// SPDX-License-Identifier: GPL-2.0-or-later

plugins {
    alias(libs.plugins.libreta.android.library)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:geometry"))
    implementation(libs.androidx.exifinterface)
    // Reading PDFs (page sizes) and writing annotated ones. BouncyCastle only serves PDFs
    // encrypted with certificates, which are rare: left out (about 10 MB); password-protected
    // PDFs use the platform's ciphers.
    implementation(libs.pdfbox.android) { exclude(group = "org.bouncycastle") }
    implementation(libs.kotlinx.coroutines.android)

    // Sample documents in screenshot tests.
    testImplementation(project(":core:format"))
    testImplementation(libs.kotlinx.coroutines.test)

    // Device tests: PdfRenderer only runs on Android itself.
    androidTestImplementation(project(":core:format"))
    androidTestImplementation(libs.androidx.test.services.storage)
    androidTestUtil(libs.androidx.test.services)
}

android {
    defaultConfig {
        // Lets device tests save rendered images, pulled into build/outputs/connected_android_test_additional_output.
        testInstrumentationRunnerArguments["useTestStorageService"] = "true"
    }
}

// The sample documents, readable by device tests as assets.
androidComponents {
    onVariants { variant ->
        variant.androidTest
            ?.sources
            ?.assets
            ?.addStaticSourceDirectory(testData.asFile.absolutePath)
    }
}

// Screenshot tests compare against PNGs in src/test/screenshots. Record new references with
//   ./gradlew :android:render:testDebugUnitTest -Plibreta.recordScreenshots=true
val screenshots: Directory = layout.projectDirectory.dir("src/test/screenshots")
val screenshotDiffs: Provider<Directory> = layout.buildDirectory.dir("screenshot-diffs")
val recordScreenshots: Boolean = providers.gradleProperty("libreta.recordScreenshots").orNull.toBoolean()
val testData: Directory = layout.settingsDirectory.dir("testdata")

// Set by scripts/render-crosscheck.sh: where RenderExportTest writes rendered sample pages.
val renderExportDir: String? = providers.gradleProperty("libreta.renderExportDir").orNull

tasks.withType<Test>().configureEach {
    // Robolectric native graphics needs more than the default 512 MB on SDK 35+.
    maxHeapSize = "3g"
    systemProperty("libreta.screenshots", screenshots.asFile.absolutePath)
    systemProperty("libreta.screenshotDiffs", screenshotDiffs.get().asFile.absolutePath)
    systemProperty("libreta.recordScreenshots", recordScreenshots.toString())
    systemProperty("libreta.testdata", testData.asFile.absolutePath)
    renderExportDir?.let { systemProperty("libreta.renderExportDir", it) }
    inputs.property("recordScreenshots", recordScreenshots)
    inputs
        .files(screenshots.asFileTree)
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("screenshots")
    inputs
        .dir(testData)
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("testdata")
    // Recording writes into the source tree, so it must always run.
    if (recordScreenshots) outputs.upToDateWhen { false }
}
