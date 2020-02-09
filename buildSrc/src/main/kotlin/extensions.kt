import org.gradle.api.Project

fun Project.junit5(name: String) =
    "org.junit.jupiter:junit-jupiter-$name:${this.properties["junitVersion"]}"
