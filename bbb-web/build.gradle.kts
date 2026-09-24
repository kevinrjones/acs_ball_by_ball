import org.gradle.api.tasks.testing.Test
import com.github.gradle.node.npm.task.NpmTask
import java.time.OffsetDateTime

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.node.gradle)
    application
}

group = "com.knowledgespike"

fun getAppVersion(): String = providers.gradleProperty("appVersion").orNull
    ?: providers.environmentVariable("BBB_WEB_APP_VERSION").orNull
    ?: providers.environmentVariable("GITHUB_REF_NAME").orNull?.takeIf { it.startsWith("v") }
    ?: "v0.1.0"

version = getAppVersion()

dependencies {
    implementation(project(":bbb-shared"))

    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.sessions)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.dotenv.kotlin)

    implementation(libs.knowledgespike.kbff)
    implementation(libs.nimbus.jose.jwt)
    implementation(libs.nimbus.oauth2.oidc.sdk)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.junit)
    testImplementation(libs.junit.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kluent)
    testImplementation(libs.strikt)
}

node {
    download.set(false)
    nodeProjectDir.set(file("${project.projectDir}/ClientApp"))
}

val npmInstallClientApp by tasks.registering(NpmTask::class) {
    npmCommand.set(listOf("ci"))
    inputs.files(
        file("${project.projectDir}/ClientApp/package.json"),
        file("${project.projectDir}/ClientApp/package-lock.json"),
    )
    outputs.dir(file("${project.projectDir}/ClientApp/node_modules"))
}

val buildClientApp by tasks.registering(NpmTask::class) {
    dependsOn(npmInstallClientApp)
    npmCommand.set(listOf("run", "build"))
    inputs.dir(file("${project.projectDir}/ClientApp/src"))
    inputs.files(
        file("${project.projectDir}/ClientApp/package.json"),
        file("${project.projectDir}/ClientApp/package-lock.json"),
        file("${project.projectDir}/ClientApp/angular.json"),
        file("${project.projectDir}/ClientApp/tailwind.config.js"),
        file("${project.projectDir}/ClientApp/tsconfig.json"),
        file("${project.projectDir}/ClientApp/tsconfig.app.json"),
    )
    outputs.dir(file("${project.projectDir}/ClientApp/dist/browser"))
}

val testClientApp by tasks.registering(NpmTask::class) {
    dependsOn(npmInstallClientApp)
    npmCommand.set(listOf("run", "test", "--", "--watch=false", "--browsers=ChromeHeadless"))
    inputs.dir(file("${project.projectDir}/ClientApp/src"))
    inputs.files(
        file("${project.projectDir}/ClientApp/package.json"),
        file("${project.projectDir}/ClientApp/package-lock.json"),
        file("${project.projectDir}/ClientApp/angular.json"),
        file("${project.projectDir}/ClientApp/tsconfig.json"),
        file("${project.projectDir}/ClientApp/tsconfig.spec.json"),
    )
    outputs.upToDateWhen { true }
}

val syncClientAppResources by tasks.registering(Sync::class) {
    dependsOn(buildClientApp)
    from(file("${project.projectDir}/ClientApp/dist/browser"))
    into(layout.buildDirectory.dir("generated/clientAppResources/static/browser"))
}

sourceSets {
    main {
        resources {
            srcDir(layout.buildDirectory.dir("generated/clientAppResources"))
        }
    }
}

tasks.named<ProcessResources>("processResources") {
    val versionProperties = mapOf(
        "version" to project.version,
        "buildDate" to OffsetDateTime.now()
    )
    inputs.properties(versionProperties)
    filesMatching("version.properties") {
        expand(versionProperties)
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(syncClientAppResources)
}

tasks.named("check") {
    dependsOn(testClientApp)
}

application {
    mainClass.set("com.knowledgespike.ballbyball.web.ApplicationKt")
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
