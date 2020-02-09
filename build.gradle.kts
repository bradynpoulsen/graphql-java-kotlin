import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintPlugin

plugins {
    base
    `quick-dependencies`
    kotlin("jvm") apply false
    id("org.jetbrains.dokka")
    id("org.jlleitschuh.gradle.ktlint")
}

allprojects {
    group = "codes.thesavage.graphqljava.kotlin"
    version = "SNAPSHOT"

    repositories {
        mavenCentral()
        jcenter()
    }
}

subprojects {
    tasks.withType<KotlinCompile>() {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }

    apply<KtlintPlugin>()
    afterEvaluate {
        tasks.named("check").configure {
            dependsOn("ktlintCheck")
        }
    }
}
