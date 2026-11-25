import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    compilerOptions { optIn.add("kotlin.time.ExperimentalTime"); freeCompilerArgs.add("-Xexpect-actual-classes") }
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(libs.junit)
        }
    }
}

tasks.withType<Test>().configureEach {
    // The data path timings (customization/speed-pass.md) load the machine; they run only when asked: CLEAN_SPEED=1 ./gradlew :core:jvmTest
    if (System.getenv("CLEAN_SPEED") == null) { exclude("**/DataPathSpeedTest*"); exclude("**/ScreenPathSpeedTest*") }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
