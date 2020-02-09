plugins {
    kotlin("jvm")
}

dependencies {
    implementation(kotlin("stdlib"))
}

dependencies {
    testImplementation(kotlin("test-junit5"))
    testImplementation(junit5("api"))
    testRuntimeOnly(junit5("engine"))
}
