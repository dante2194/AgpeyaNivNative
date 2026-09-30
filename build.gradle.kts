plugins {
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("com.android.application") version "9.4.1" apply false
    // AGP 9.x provides Kotlin + Compose support internally.
    // Declaring org.jetbrains.kotlin.android / kotlin.plugin.compose
    // here causes the build to fail with:
    //   "The 'org.jetbrains.kotlin.android' plugin is no longer
    //    required for Kotlin support since AGP 9.0."
}
