import org.gradle.api.tasks.testing.Test

plugins {
    alias(ktorlibs.plugins.ktor)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.jooq)
    application
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(libs.jooq.codegen)
        classpath(libs.mariadb)
    }
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

sourceSets {
    main {
        java.srcDir(layout.projectDirectory.dir("src/generated/jooq/kotlin"))
    }
}

val jooqDatabaseUrl = providers.environmentVariable("JOOQ_DATABASE_URL")
    .orElse("jdbc:mariadb://localhost:3306/acs_ball_by_ball")
val jooqDatabaseUser = providers.environmentVariable("JOOQ_DATABASE_USER")
    .orElse("ballbyball")
val jooqDatabasePassword = providers.environmentVariable("JOOQ_DATABASE_PASSWORD")
    .orElse("p4ssw0rd")
val jooqDatabaseSchema = providers.environmentVariable("JOOQ_DATABASE_SCHEMA")
    .orElse("acs_ball_by_ball")
val jooqOutputSchema = providers.environmentVariable("JOOQ_OUTPUT_SCHEMA")
    .orElse("acs_ball_by_ball")

jooq {
    configuration {
        basedir = layout.projectDirectory.dir("src").asFile.path
        jdbc {
                    driver = "org.mariadb.jdbc.Driver"
                    url = jooqDatabaseUrl.get()
                    user = jooqDatabaseUser.get()
                    password = jooqDatabasePassword.get()
                }
                generator {
                    name = "org.jooq.codegen.KotlinGenerator"
                    database {
                        name = "org.jooq.meta.mariadb.MariaDBDatabase"
                        inputSchema = jooqDatabaseSchema.get()
                        outputSchema = jooqOutputSchema.get()
//                        includes = "(?i:(dim_match|dim_date|dim_team|dim_ground|fact_match|dim_innings|fact_delivery|dim_person|dim_wicket|bridge_delivery_wicket|bridge_delivery_fielder))"
                        forcedTypes {
                            forcedType {
                                name = "BIGINT"
                                includeExpression = "(?i:.*\\.(match_key|team1_key|team2_key|ground_key|toss_team_key|winner_team_key|loser_team_key|team_key|person_key|innings_key|delivery_key|wicket_key|batter_key|non_striker_key|bowler_key|batting_team_key|bowling_team_key))"
                            }
                        }
                    }
                    generate {
                        isPojos = false
                        isDaos = false
                        isRelations = false
                    }
                    target {
                        packageName = "com.knowledgespike.ballbyball.api.generated.jooq"
                        directory = "generated/jooq/kotlin"
                        isClean = true
                    }
                }
    }
}