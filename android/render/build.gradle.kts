plugins {
    alias(libs.plugins.inkpad.android.library)
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:geometry"))
}
