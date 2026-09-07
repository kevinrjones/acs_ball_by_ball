plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.knowledgespike"
version = "1.0"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(kotlin("stdlib-jdk8"))

    implementation(libs.logback.classic)
    implementation(libs.logback.core)
    implementation(libs.angus.mail)
    implementation(libs.angus.activation)

    testImplementation(libs.junit)
    testImplementation(libs.junit.engine)
    testImplementation(libs.kluent)
    testImplementation(libs.strikt)

    implementation(libs.kotlin.coroutines)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.serialization)
    implementation(libs.hikari.cp)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

kotlin {
    jvmToolchain(21)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}