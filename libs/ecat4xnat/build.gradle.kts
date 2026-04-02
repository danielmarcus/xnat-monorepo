plugins {
    id("xnat-java-library")
}

group = "org.nrg"
version = libs.versions.xnat.get()

dependencies {
    // --- Internal modules ---
    api(project(":build-tools:xnat-data-models"))
    api(project(":libs:extattr"))
    api(project(":libs:session-builders"))

    // --- DICOM ---
    implementation(libs.dcm4che2.core)

    // --- Utilities ---
    implementation(libs.commons.lang3)
    implementation(libs.guava)
    implementation(libs.slf4j.api)

    // --- Runtime ---
    runtimeOnly(libs.log4j.over.slf4j)

    // --- Test ---
    testImplementation(libs.junit4)
}
