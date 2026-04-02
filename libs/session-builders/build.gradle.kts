plugins {
    id("xnat-java-library")
}

group = "org.nrg"
version = libs.versions.xnat.get()

dependencies {
    // --- Internal modules ---
    api(project(":libs:extattr"))
    api(project(":build-tools:xnat-data-models"))
    api(project(":libs:xdat"))

    // --- DICOM ---
    implementation(libs.dcm4che2.core)
    implementation(libs.dcm4che5.core)

    // --- Utilities ---
    implementation(libs.commons.lang3)
    implementation("commons-lang:commons-lang:2.6")
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.simple)
    implementation(libs.guava)

    // --- Test ---
    testImplementation(libs.junit4)
}
