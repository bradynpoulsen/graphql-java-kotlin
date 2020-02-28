import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.kotlin.dsl.provideDelegate


fun Project.junit5(name: String): String {
    val junitVersion: String by project
    return "org.junit.jupiter:junit-jupiter-$name:$junitVersion"
}

fun Project.coroutines(name: String): String {
    val coroutinesVersion: String by project
    return "org.jetbrains.kotlinx:kotlinx-coroutines-$name:$coroutinesVersion"
}

val graphqlJavaNotation get() = "com.graphql-java:graphql-java"
fun Project.graphqlJavaVersion(externalModuleDependency: ExternalModuleDependency) = with(externalModuleDependency) {
    version {
        val graphqlJavaVersion: String by project
        val graphqlJavaVersionMinimum: String by project
        strictly("[$graphqlJavaVersionMinimum, )")
        prefer(graphqlJavaVersion)
    }
}
