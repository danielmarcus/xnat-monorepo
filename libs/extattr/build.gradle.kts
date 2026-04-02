plugins {
    id("xnat-java-library")
}

group = "org.nrg"
version = libs.versions.xnat.get()

dependencies {
    implementation(libs.commons.lang3)
    implementation(libs.guava)
    implementation(libs.slf4j.api)
}
