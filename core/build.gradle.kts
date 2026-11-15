import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
}

tasks.test {
    // The data path timings (customization/speed-pass.md) load the machine; they run only when asked: CLEAN_SPEED=1 ./gradlew :core:test
    if (System.getenv("CLEAN_SPEED") == null) exclude("**/DataPathSpeedTest*")
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
