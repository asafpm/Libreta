// SPDX-License-Identifier: GPL-2.0-or-later

plugins {
    alias(libs.plugins.inkpad.android.library)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:geometry"))
    implementation(libs.androidx.exifinterface)

    // Sample documents in screenshot tests.
    testImplementation(project(":core:format"))
}

// Screenshot tests compare against PNGs in src/test/screenshots. Record new references with
//   ./gradlew :android:render:testDebugUnitTest -Pinkpad.recordScreenshots=true
val screenshots: Directory = layout.projectDirectory.dir("src/test/screenshots")
val screenshotDiffs: Provider<Directory> = layout.buildDirectory.dir("screenshot-diffs")
val recordScreenshots: Boolean = providers.gradleProperty("inkpad.recordScreenshots").orNull.toBoolean()
val testData: Directory = layout.settingsDirectory.dir("testdata")

tasks.withType<Test>().configureEach {
    // Robolectric native graphics needs more than the default 512 MB on SDK 35+.
    maxHeapSize = "3g"
    systemProperty("inkpad.screenshots", screenshots.asFile.absolutePath)
    systemProperty("inkpad.screenshotDiffs", screenshotDiffs.get().asFile.absolutePath)
    systemProperty("inkpad.recordScreenshots", recordScreenshots.toString())
    systemProperty("inkpad.testdata", testData.asFile.absolutePath)
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
