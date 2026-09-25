import org.gradle.api.tasks.testing.Test

plugins {
    alias(ktorlibs.plugins.ktor)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "com.knowledgespike"
version = "0.1.0"

dependencies {
    implementation(project(":bbb-shared"))

    implementation(libs.arrow.core)
    implementation(ktorlibs.client.apache)
    implementation(ktorlibs.client.core)

    implementation(ktorlibs.serialization.kotlinx.json)
    implementation(ktorlibs.server.config.yaml)
    implementation(ktorlibs.server.core)
    implementation(ktorlibs.server.statusPages)
    implementation(ktorlibs.server.resources)
    implementation(ktorlibs.server.auth)
    implementation(ktorlibs.server.auth.jwt)
    implementation(ktorlibs.server.csrf)
    implementation(ktorlibs.server.doubleReceive)
    implementation(ktorlibs.server.compression)
    implementation(ktorlibs.server.swagger)
    implementation(ktorlibs.server.callLogging)
    implementation(ktorlibs.server.callId)
    implementation(ktorlibs.server.metrics.micrometer)
    implementation(ktorlibs.server.metrics)
    implementation(ktorlibs.server.contentNegotiation)
    implementation(ktorlibs.server.netty)
    implementation(ktorlibs.network.tls.certificates)

    implementation(libs.jwks.rsa)

    implementation(libs.hikari.cp)
    implementation(libs.jooq)
    // Loads local .env configuration into JVM system properties on startup

    implementation(libs.dotenv.kotlin)

    runtimeOnly(libs.mariadb)
    runtimeOnly(libs.postgres)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.junit)
    testImplementation(libs.junit.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kluent)
    testImplementation(libs.strikt)
}

application {
    mainClass.set("com.knowledgespike.ballbyball.api.ApplicationKt")
    applicationDefaultJvmArgs = listOf("-Djava.net.preferIPv4Stack=true")
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