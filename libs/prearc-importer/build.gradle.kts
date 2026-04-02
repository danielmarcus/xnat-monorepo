plugins {
    id("xnat-java-library")
}

group = "org.nrg"
version = libs.versions.xnat.get()

dependencies {
    // --- Internal modules ---
    api(project(":libs:framework")) {
        isTransitive = false
    }
    api(project(":libs:dicom-xnat:dicom-xnat-mx"))
    api(project(":libs:ecat4xnat"))
    api(project(":libs:dicomtools"))
    api(project(":libs:dicom-image-utils"))

    // --- Spring ---
    implementation(libs.spring.core)

    // --- DICOM ---
    implementation(libs.dcm4che2.core)
    implementation(libs.dcm4che5.core)
    implementation("org.nrg.dicom:mizer:${project.version}")

    // --- Utilities ---
    implementation(libs.commons.lang3)
    implementation(libs.ant)
    implementation(libs.dom4j)
    implementation(libs.slf4j.api)

    // --- Compile-only ---
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    // --- Test ---
    testImplementation(libs.junit4)
    testImplementation(libs.hsqldb)
}
