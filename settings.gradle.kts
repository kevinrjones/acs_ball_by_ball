import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "BallByBall"

listOf(
    "bb-update-database",
    "bb-shared",
    "bb-api",
    "bb-web",
).forEach { projectName ->
    if (rootDir.resolve(projectName).isDirectory) {
        include(projectName)
    }
}

