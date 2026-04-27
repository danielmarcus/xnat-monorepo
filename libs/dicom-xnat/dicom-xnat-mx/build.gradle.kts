plugins {
    id("xnat-java-library")
}

group = "org.nrg"
version = libs.versions.xnat.get()

dependencies {
    // --- Internal modules ---
    api(project(":build-tools:xnat-data-models")) {
        isTransitive = false
    }
    api(project(":libs:xdat")) {
        isTransitive = false
    }
    api(project(":libs:prefs"))
    api(project(":libs:dicomtools"))
    api(project(":libs:dicom-xnat:dicom-xnat-sop"))
    api(project(":libs:dicom-xnat:dicom-xnat-util"))
    api(project(":libs:extattr"))
    api(project(":libs:session-builders"))
    api(project(":libs:notify"))

    // --- DICOM ---
    implementation(libs.dcm4che5.core)
    implementation(libs.dcm4che5.net)
    implementation(libs.dcm4che2.core)
    implementation("org.dcm4che:dcm4che-dict:${libs.versions.dcm4che5.get()}")

    // --- Swagger ---
    implementation(libs.swagger.springmvc)
    implementation(libs.swagger.ui)

    // --- Jakarta Inject ---
    implementation("jakarta.inject:jakarta.inject-api:1.0.3")

    // --- External: mizer (from XNAT Artifactory) ---
    implementation("org.nrg.dicom:mizer:${project.version}")

    // --- Servlet ---
    compileOnly(libs.javax.servlet.api)

    // --- Utilities ---
    implementation(libs.commons.lang3)
    implementation("commons-lang:commons-lang:2.6")
    implementation(libs.guava)
    implementation(libs.slf4j.api)
    implementation(libs.log4j.over.slf4j)

    // --- Runtime ---
    runtimeOnly(libs.commons.configuration)
    runtimeOnly(libs.hsqldb)

    // --- Test ---
    // libs:test exposes org.nrg.test.workers.resources.ResourceManager used
    // by Scan4TestCase to load DICOM fixtures from the classpath.
    testImplementation(project(":libs:test"))
    testImplementation(libs.junit4)
    testImplementation(libs.ant)
    testImplementation(libs.commons.configuration)
}
