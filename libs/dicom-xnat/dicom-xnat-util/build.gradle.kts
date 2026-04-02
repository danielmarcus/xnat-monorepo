plugins {
    id("xnat-java-library")
}

group = "org.nrg"
version = libs.versions.xnat.get()

dependencies {
    // --- Internal modules ---
    api(project(":libs:framework"))

    // --- DICOM ---
    implementation(libs.dcm4che5.core)
    implementation(libs.dcm4che5.net)
    implementation("org.dcm4che:dcm4che-dict:${libs.versions.dcm4che5.get()}")

    // --- Utilities ---
    implementation(libs.guava)
    implementation(libs.commons.lang3)
    implementation(libs.slf4j.api)

    // --- Test ---
    testImplementation(libs.junit4)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
    testImplementation(libs.h2)
    testImplementation(libs.hibernate.jcache)
    testImplementation(libs.slf4j.simple)
}
