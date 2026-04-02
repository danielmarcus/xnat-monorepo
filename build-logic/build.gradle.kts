plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    // Required by xnat-java-library and xnat-war-application convention plugins
    implementation("io.freefair.gradle:lombok-plugin:9.1.0")
}
