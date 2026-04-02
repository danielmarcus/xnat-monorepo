plugins {
    id("xnat-java-library")
}

group = "org.nrg"
version = libs.versions.xnat.get()

dependencies {
    implementation(libs.commons.lang3)
    implementation(libs.commons.io)
    implementation(libs.slf4j.api)

    testImplementation(libs.junit4)
    testImplementation(libs.assertj.core)
}
