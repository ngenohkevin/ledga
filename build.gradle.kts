plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.jvm") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0" apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
    id("com.google.dagger.hilt.android") version "2.57.1" apply false
    id("androidx.room") version "2.8.4" apply false
    // Roborazzi 1.60.0 is the last release built with Kotlin 2.0 metadata; 1.61+ ship 2.3 metadata,
    // which this project's Kotlin 2.1 compiler can't read (refinement R22). Upgrade together with Kotlin.
    id("io.github.takahirom.roborazzi") version "1.60.0" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
