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
}
