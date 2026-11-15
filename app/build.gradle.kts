import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.FilterConfiguration

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// The app is offline (see README.md): the merged manifest, libraries included, must never ask for the INTERNET permission.
// Raise by one for every APK handed out; each split APK's own code is made from it in onVariants below.
val VERSION_CODE = 2

// The ABIs that get their own APK, with the digit each adds to the versionCode (see onVariants below).
val ABI_CODES = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86_64" to 3)

abstract class CheckNoInternet : DefaultTask() {
    @get:InputFile abstract val manifest: RegularFileProperty

    @TaskAction fun check() {
        if ("android.permission.INTERNET" in manifest.get().asFile.readText()) {
            throw GradleException("The merged manifest requests android.permission.INTERNET, but Clean must stay offline. Find which library adds it in ${manifest.get().asFile}.")
        }
    }
}

androidComponents {
    onVariants { variant ->
        val check = tasks.register<CheckNoInternet>("check${variant.name.replaceFirstChar { it.uppercase() }}NoInternet") {
            manifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
        }
        tasks.matching { it.name == "assemble${variant.name.replaceFirstChar { c -> c.uppercase() }}" }.configureEach { dependsOn(check) }

        // One versionCode per APK of the split below: VERSION_CODE x 10 plus a digit for the ABI
        // (0 universal, 1 armeabi-v7a, 2 arm64-v8a, 3 x86_64). Android will not install a lower code over a higher one
        // without an uninstall, which deletes the data, so a phone should stay with one kind of APK.
        variant.outputs.forEach { output ->
            val abi = output.filters.find { it.filterType == FilterConfiguration.FilterType.ABI }?.identifier
            val digit = ABI_CODES[abi] ?: 0
            output.versionCode.set(VERSION_CODE * 10 + digit)
        }
    }
}

android {
    namespace = "clean.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "clean.app"
        minSdk = 26
        targetSdk = 37
        versionCode = VERSION_CODE
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // An APK per processor family, plus one universal APK that runs everywhere. Only the androidx graphics
    // library ships native code, so the split APKs are only a little smaller; they exist so a release can offer the right one.
    splits {
        abi {
            isEnable = true
            reset()
            include(*ABI_CODES.keys.toTypedArray())
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            // The build that goes on the phone: shrunk and optimised, which a debug build is not, and
            // signed with this PC's debug key like every APK before it, so it installs over them and keeps the data.
            optimization {
                enable = true
            }
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    // Robolectric's rendered-frame tests of the page transitions (MotionFramesTest) need the app's resources.
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric's native graphics cannot load its Android jar from a path with a space in it (this PC's user folder has one), so it
        // reads the jar it downloaded into ~/.m2 by the folder's short name, which has none.
        // Set ROBOLECTRIC_DEPS to that short folder name to run the rendered-frame and summary tests; without it they are left out of the run.
        unitTests.all { test ->
            val deps = System.getenv("ROBOLECTRIC_DEPS")
            if (deps == null) { test.exclude("**/MotionFramesTest*"); test.exclude("**/SummaryExercisesTest*"); test.exclude("**/ReorderHoldTest*"); test.exclude("**/StartupSpeedTest*"); test.exclude("**/ScreensTourTest*"); test.exclude("**/CorrectionTest*"); test.exclude("**/IsoFixOnceTest*") }
            else {
                test.systemProperty("robolectric.offline", "true")
                test.systemProperty("robolectric.dependency.dir", deps)
                // The look ScreensTourTest draws in.
                System.getenv("CLEAN_PRESET")?.let { test.systemProperty("preset", it) }
            }
        }
    }
}

// Room writes the schema of each database version here, kept in git, so a migration can be checked against what it must reach.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.core)
    // Writing workouts and weigh-ins to Health Connect, which is on the phone; it brings no network access.
    implementation(libs.androidx.health.connect)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}