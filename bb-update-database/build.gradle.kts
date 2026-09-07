plugins {
    alias(libs.plugins.flyway)
    application
}

buildscript {
    dependencies {
        classpath(libs.flyway)
    }
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

