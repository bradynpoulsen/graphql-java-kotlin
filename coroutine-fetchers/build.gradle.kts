import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.dokka")
}

dependencies {
    api(coroutines("core"))
    implementation(coroutines("jdk8"))
    api(graphqlJavaNotation, project::graphqlJavaVersion)
}

dependencies {
    testImplementation(kotlin("test-junit5"))
    testImplementation(junit5("api"))
    testRuntimeOnly(junit5("engine"))
}

tasks.withType<KotlinCompile>().all {
    kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlin.Experimental"
}
