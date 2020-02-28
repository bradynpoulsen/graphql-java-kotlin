import org.jetbrains.dokka.gradle.DokkaPlugin
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintPlugin

plugins {
    base
    `quick-dependencies`
    kotlin("jvm")
    id("org.jetbrains.dokka")
    id("org.jlleitschuh.gradle.ktlint") apply false
}

allprojects {
    group = "codes.thesavage.graphqljava.kotlin"
    version = "SNAPSHOT"

    repositories {
        mavenCentral()
        jcenter()
    }

    apply<DokkaPlugin>()
    tasks.withType<DokkaTask>().all {
        configuration {
            jdkVersion = 8
        }
    }
}

subprojects {
    tasks.withType<KotlinCompile>() {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }

    apply<KotlinPluginWrapper>()
    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }

    apply<KtlintPlugin>()
    afterEvaluate {
        tasks.named("compileKotlin").configure {
            dependsOn("ktlintFormat")
        }
    }
}

tasks {
    val dokka by getting(DokkaTask::class) {
        subProjects = listOf("coroutine-fetchers", "schema")

        configuration {
            perPackageOption {
                prefix = "codes.thesavage.graphqljava.kotlin"
                reportUndocumented = true
            }
        }
    }
}
