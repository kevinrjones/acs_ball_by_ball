import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask


plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.version.update)
    alias(libs.plugins.catalog.update)
    alias(libs.plugins.kover).apply(false)
    alias(libs.plugins.pitest).apply(false)
}

allprojects {
    group = "com.knowledgespike"
    version = "0.1.0"

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "kotlinx-serialization")

    repositories {
        mavenCentral()
        mavenLocal()
    }

    dependencies {

        testImplementation(kotlin("test"))
        implementation(kotlin("stdlib-jdk8"))

        implementation(rootProject.libs.logback.classic)
        implementation(rootProject.libs.logback.core)
        implementation(rootProject.libs.angus.mail)
        implementation(rootProject.libs.angus.activation)

        testImplementation(rootProject.libs.junit)
        testImplementation(rootProject.libs.j.unit.engine)
        testImplementation(rootProject.libs.kluent)
        testImplementation(rootProject.libs.strikt)

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

}

subprojects {
    version = "1.0"

}

project(":bb-shared") {
    val sourceSets = extensions.getByName("sourceSets") as SourceSetContainer
    val integrationTestSourceSet = sourceSets.create("integrationTest") {
        java.srcDir("src/integrationTest/kotlin")
        resources.srcDir("src/integrationTest/resources")
        compileClasspath += sourceSets["main"].output + configurations["testRuntimeClasspath"]
        runtimeClasspath += output + compileClasspath
    }

    configurations[integrationTestSourceSet.implementationConfigurationName]
        .extendsFrom(configurations["testImplementation"])
    configurations[integrationTestSourceSet.runtimeOnlyConfigurationName]
        .extendsFrom(configurations["testRuntimeOnly"])

    dependencies {
        implementation(rootProject.libs.kotlin.coroutines)
        implementation(rootProject.libs.kotlin.reflect)
        implementation(rootProject.libs.kotlinx.serialization)
        implementation(rootProject.libs.hikari.cp)
        implementation(rootProject.libs.jsoup)
    }

    tasks.register("integrationTest", Test::class) {
        description = "Runs the sa-shared integration tests"
        group = "verification"
        testClassesDirs = integrationTestSourceSet.output.classesDirs
        classpath = integrationTestSourceSet.runtimeClasspath
        shouldRunAfter(tasks.named("test"))
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}


project(":bb-update-database") {
    dependencies {
        implementation(rootProject.libs.commons.cli)
        implementation(rootProject.libs.kotlinx.serialization)
        implementation(rootProject.libs.mariadb)

        implementation(project(":bb-shared"))
    }
}



fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}

tasks.withType<DependencyUpdatesTask> {
    rejectVersionIf {
        isNonStable(candidate.version)
    }
}