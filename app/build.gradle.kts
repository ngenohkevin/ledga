import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("androidx.room")
    id("io.github.takahirom.roborazzi")
}

// Spec §13.1, R132: the version comes from version.properties. `-Pledga.versionName=…` builds another one (the S26
// update test); the release workflow refuses a tag that doesn't match version.properties.
val ledgaVersionName: String = providers.gradleProperty("ledga.versionName").orNull
    ?: Properties().apply {
        load(providers.fileContents(rootProject.layout.projectDirectory.file("version.properties")).asText.get().reader())
    }.getProperty("VERSION_NAME")
    ?: throw GradleException("version.properties has no VERSION_NAME")

/** Spec §13.1: major·1,000,000 + minor·10,000 + patch·100 + stage (N for -beta.N, 99 for stable). Mirrors `AppVersion.code`. */
fun versionCodeOf(name: String): Int {
    val m = Regex("""(\d{1,4})\.(\d{1,2})\.(\d{1,2})(?:-beta\.(\d{1,2}))?""").matchEntire(name)
        ?: throw GradleException("VERSION_NAME \"$name\" must be X.Y.Z or X.Y.Z-beta.N")
    val (major, minor, patch) = (1..3).map { m.groupValues[it].toInt() }
    val beta = m.groupValues[4].takeIf { it.isNotEmpty() }?.toInt()
    if (major > 2_000) throw GradleException("VERSION_NAME \"$name\": the major version is too large for a versionCode")
    if (beta != null && beta !in 1..98) throw GradleException("VERSION_NAME \"$name\": a beta number must be 1 to 98")
    return major * 1_000_000 + minor * 10_000 + patch * 100 + (beta ?: 99)
}

val ledgaVersionCode = versionCodeOf(ledgaVersionName)

/** Spec §13.2: copies `release-notes/<VERSION_NAME>.md` into the APK as `release-notes/current.md` (What's new, R141). */
abstract class BundleReleaseNotes : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val notes: ConfigurableFileCollection

    @get:Input
    abstract val versionName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile.resolve("release-notes")
        out.deleteRecursively()
        out.mkdirs()
        // A build with no notes for its version has no What's new; the release workflow refuses to publish it.
        notes.files.firstOrNull { it.name == "${versionName.get()}.md" }?.copyTo(out.resolve("current.md"))
    }
}

// R162: the release key's password is never in this file. CI passes it in the environment; a local build reads the
// gitignored keystore/keystore.properties. Without either, the release build stops at Gradle's signing check.
val keystoreSecrets = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("keystore/keystore.properties")).asText.orNull?.let { load(it.reader()) }
}

fun keystoreSecret(env: String, key: String): String? = providers.environmentVariable(env).orNull ?: keystoreSecrets.getProperty(key)

android {
    namespace = "com.ledga.app"
    compileSdk = 36

    signingConfigs {
        create("release") {
            val keystoreFile = rootProject.file("keystore/ledga-release.jks")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = keystoreSecret("KEYSTORE_PASSWORD", "storePassword")
                keyAlias = keystoreSecret("KEY_ALIAS", "keyAlias") ?: "ledga"
                keyPassword = keystoreSecret("KEY_PASSWORD", "keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.ledga.app"
        minSdk = 26
        targetSdk = 35
        versionCode = ledgaVersionCode
        versionName = ledgaVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // R25: debug builds are their own app ("Ledga dev"), so testing never touches the real v1 install.
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.md"
            )
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

androidComponents {
    onVariants { variant ->
        val bundle = tasks.register<BundleReleaseNotes>("bundle${variant.name.replaceFirstChar(Char::uppercase)}ReleaseNotes") {
            notes.from(rootProject.fileTree("release-notes") { include("*.md") })
            versionName.set(ledgaVersionName)
        }
        variant.sources.assets?.addGeneratedSourceDirectory(bundle, BundleReleaseNotes::outputDir)
    }
}

// R146: the release workflow passes -Pledga.releaseFiles=required, so ReleaseManifestFileTest fails rather than skips
// when the release files are missing. A system property is a task input, so changing it reruns the test (an environment
// variable isn't one, and a cached up-to-date run would ignore it).
// R160: -Pledga.v1Export=<v1's export> runs the opt-in upgrade rehearsal; empty skips it.
// R163 (final review M12): the release files the read-back test reads are an input too, so new ones rerun it.
tasks.withType<Test>().configureEach {
    systemProperty("ledga.releaseFiles", providers.gradleProperty("ledga.releaseFiles").getOrElse("optional"))
    systemProperty("ledga.v1Export", providers.gradleProperty("ledga.v1Export").getOrElse(""))
    inputs.files(layout.buildDirectory.dir("ledga-release")).withPropertyName("ledgaReleaseFiles").withPathSensitivity(PathSensitivity.RELATIVE)
}

// R146: what the release workflow and scripts/update-test-server.sh read (`./gradlew -q :app:ledgaVersion`).
tasks.register("ledgaVersion") {
    val name = ledgaVersionName
    val code = ledgaVersionCode
    val minSdk = android.defaultConfig.minSdk
    doLast {
        println("VERSION_NAME=$name")
        println("VERSION_CODE=$code")
        println("MIN_SDK=$minSdk")
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2026.03.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Activity & Lifecycle
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.core:core-ktx:1.16.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.9.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Room
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.57.1")
    ksp("com.google.dagger:hilt-compiler:2.57.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.11.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Splash Screen
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // v2 core (pure Kotlin domain)
    implementation(project(":core"))

    // Room paging (v2 transactions list)
    implementation("androidx.room:room-paging:2.8.4")
    // Paging stays on 3.3.x with room-paging (3.4+ may need newer Kotlin metadata, as Roborazzi 1.61 did).
    implementation("androidx.paging:paging-compose:3.3.6")

    // v2 data-layer tests
    testImplementation(kotlin("test-junit"))
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.work:work-testing:2.11.1")

    // v2 design system tests (Phase 3): Compose UI tests + Roborazzi screenshots on Robolectric
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.60.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.60.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("androidx.paging:paging-testing:3.3.6")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
