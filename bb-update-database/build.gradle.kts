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

    implementation(libs.commons.cli)
    implementation(libs.kotlinx.serialization)
    implementation(libs.mariadb)
    implementation(project(":bb-shared"))
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

application {
    mainClass.set("com.knowledgespike.cricsheet.parse.Application")

    applicationDefaultJvmArgs = listOf("-Dlogback.configurationFile=./logging/logback.xml")
}


flyway {
    url = "jdbc:mysql://localhost:3306/cricsheet"
    user = "cricsheet"
    password = "p4ssw0rd"
    schemas = arrayOf("cricsheet")
    locations = arrayOf("filesystem:${projectDir}/migrations/mysql")
    sqlMigrationPrefix = ""
    baselineOnMigrate = true
    outOfOrder = true
}

