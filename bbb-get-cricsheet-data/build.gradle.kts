import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

group = "com.knowledgespike"
version = "1.0"

dependencies {
    implementation(project(":bbb-shared"))
    implementation(libs.commons.cli)
    implementation(libs.jsoup)
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
    mainClass.set("com.knowledgespike.ballbyball.getcricsheetdata.Application")
}