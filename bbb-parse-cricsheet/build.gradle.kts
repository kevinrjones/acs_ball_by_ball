import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "com.knowledgespike"
version = "1.0"

dependencies {
    implementation(project(":bbb-cli-shared"))
    implementation(libs.commons.cli)
    implementation(libs.kotlinx.serialization)
    implementation(libs.logback.classic)
    implementation(libs.logback.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
    testImplementation(libs.junit.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.strikt)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.knowledgespike.ballbyball.parsecricsheet.Application")
}