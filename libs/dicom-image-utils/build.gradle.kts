plugins {
    id("xnat-java-library")
}

group = "org.nrg"
version = libs.versions.xnat.get()

dependencies {
    // --- DICOM ---
    implementation(libs.dcm4che5.core)
    implementation(libs.dcm4che5.image)
    implementation(libs.dcm4che5.imageio)

    // --- External: mizer (from XNAT Artifactory) ---
    implementation("org.nrg.dicom:mizer:${project.version}")

    // --- Imaging ---
    implementation("net.imagej:ij:1.54f")

    // --- Utilities ---
    implementation(libs.commons.lang3)
    implementation(libs.slf4j.api)

    // --- Test ---
    testImplementation(libs.junit4)
}
