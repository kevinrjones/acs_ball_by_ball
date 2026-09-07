rootProject.name = "StatsApp"

include("bb-update-database")
include("bb-shared")


buildscript {
    repositories {
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
    }
    dependencies {
        classpath("org.gradle.toolchains:foojay-resolver:1.0.0")
    }
}

apply(plugin = "org.gradle.toolchains.foojay-resolver-convention")

//toolchainManagement {
//    jvm {
//        javaRepositories {
//            repository("foojay") {
//                resolverClass.set(org.gradle.toolchains.foojay.FoojayToolchainResolver::class.java)
//            }
//        }
//    }
//}

