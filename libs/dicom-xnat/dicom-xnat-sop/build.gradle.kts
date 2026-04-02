plugins {
    id("xnat-java-library")
}

group = "org.nrg"
version = libs.versions.xnat.get()

dependencies {
    // --- DICOM ---
    implementation(libs.dcm4che5.core)
    implementation(libs.dcm4che5.net)
    implementation("org.dcm4che:dcm4che-dict:${libs.versions.dcm4che5.get()}")

    // --- Utilities ---
    implementation(libs.guava)
    implementation(libs.commons.lang3)
    implementation(libs.commons.io)
    implementation(libs.slf4j.api)

    // --- Test ---
    testImplementation(libs.junit4)
    testImplementation(libs.slf4j.simple)
}
