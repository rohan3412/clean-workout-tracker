import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    compilerOptions { optIn.add("kotlin.time.ExperimentalTime"); optIn.add("androidx.compose.ui.ExperimentalComposeUiApi"); freeCompilerArgs.add("-Xexpect-actual-classes") }
    androidLibrary {
        namespace = "clean.app.shared"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
    }
    // The framework the Xcode app (iosApp/) links: static, so the app is one binary. Built by `embedAndSignAppleFrameworkForXcode` from Xcode's build phase.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            api(libs.compose.ui.backhandler)
            api(libs.compose.material3)
            api(libs.compose.components.resources)
            api(libs.androidx.lifecycle.viewmodel)
            api(libs.androidx.lifecycle.viewmodel.savedstate)
            api(libs.androidx.room.runtime)
            api(libs.kotlinx.serialization.json)
            api(libs.haze)
            implementation(libs.atomicfu)
        }
        iosMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "clean.app.generated.resources"
}

// Room writes the schema of each database version here, kept in git, so a migration can be checked against what it must reach.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}
