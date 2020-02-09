pluginManagement {
    val kotlinVersion: String by settings
    val dokkaVersion: String by settings
    val ktlintVersion: String by settings

    plugins {
        kotlin("jvm") version kotlinVersion
        id("org.jetbrains.dokka") version dokkaVersion
        id("org.jlleitschuh.gradle.ktlint") version ktlintVersion
    }
}

rootProject.name = "graphql-java-kotlin"
include(":coroutine-fetchers")
include(":schema")
