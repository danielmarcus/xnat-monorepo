plugins {
    id("xnat-java-library")
}

group = "org.nrg"
version = libs.versions.xnat.get()

dependencies {
    implementation(libs.spring.jdbc)
    implementation(libs.spring.test)
    implementation(libs.spring.context)
    implementation(libs.jackson.databind)
    implementation(libs.h2)
    implementation(libs.slf4j.api)
    implementation(libs.commons.lang3)
    implementation(libs.commons.io)
    implementation(libs.junit4)
    implementation(libs.junit.jupiter.api)
    implementation(libs.junit.jupiter.engine)
    implementation(libs.mockito.core)
}
