import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The app's logic, ported from the Swift GadgetKit package: plain Kotlin with
// no Android dependencies, so its tests run on any JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    api(libs.coroutines.core)
    implementation(libs.serialization.json)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.coroutines.test)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileTestKotlin") {
    // runCurrent and advanceTimeBy drive the virtual clock.
    compilerOptions.optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
}

tasks.test {
    useJUnitPlatform()
    // The reply samples both ports test against (fixtures/README.md).
    val fixtures = rootProject.layout.projectDirectory.dir("../fixtures/replies")
    inputs.dir(fixtures).withPropertyName("fixtures")
    systemProperty("fixtures.replies", fixtures.asFile.absolutePath)
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
