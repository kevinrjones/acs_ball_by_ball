import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.flyway)
    application
}

buildscript {
    dependencies {
        classpath(libs.flyway)
    }
}

group = "com.knowledgespike"
version = "1.0"

dependencies {
    testImplementation(kotlin("test"))

    implementation(libs.logback.classic)
    implementation(libs.logback.core)
    implementation(libs.angus.mail)
    implementation(libs.angus.activation)

    testImplementation(libs.junit)
    testImplementation(libs.junit.engine)
    testImplementation(libs.kluent)
    testImplementation(libs.strikt)

    implementation(libs.commons.cli)
    implementation(libs.kotlinx.serialization)
    implementation(libs.mariadb)
    implementation(project(":bb-shared"))
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
    mainClass.set("com.knowledgespike.cricsheet.parse.Application")

    applicationDefaultJvmArgs = listOf("-Dlogback.configurationFile=./logging/logback.xml")
}


flyway {
    url = providers.gradleProperty("flyway.url")
        .orElse(providers.environmentVariable("FLYWAY_URL"))
        .orElse("jdbc:mysql://localhost:3306/cricsheet")
        .get()
    user = providers.gradleProperty("flyway.user")
        .orElse(providers.environmentVariable("FLYWAY_USER"))
        .orElse("cricsheet")
        .get()
    password = providers.gradleProperty("flyway.password")
        .orElse(providers.environmentVariable("FLYWAY_PASSWORD"))
        .orElse("")
        .get()
    schemas = arrayOf("cricsheet")
    locations = arrayOf("filesystem:${projectDir}/migrations/mysql")
    sqlMigrationPrefix = ""
    baselineOnMigrate = true
    outOfOrder = true
}

