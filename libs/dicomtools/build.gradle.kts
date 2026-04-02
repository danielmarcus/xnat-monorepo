plugins {
    id("xnat-java-library")
}

group = "org.nrg"
version = libs.versions.xnat.get()

dependencies {
    // --- Internal modules ---
    api(project(":libs:framework"))
    api(project(":libs:config")) {
        isTransitive = false
    }
    api(project(":libs:extattr"))

    // --- DICOM ---
    implementation(libs.dcm4che2.core)
    implementation(libs.dcm4che2.net)
    implementation(libs.dcm4che2.iod)
    implementation(libs.dcm4che5.core)
    implementation(libs.dcm4che5.net)

    // --- External: mizer (from XNAT Artifactory) ---
    implementation("org.nrg.dicom:mizer:${project.version}")

    // --- Utilities ---
    implementation(libs.commons.lang3)
    implementation(libs.commons.io)
    implementation(libs.slf4j.api)
    implementation(libs.hsqldb)

    // --- Compile-only ---
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    // --- Test ---
    testImplementation(project(":libs:automation")) {
        exclude(group = "org.codehaus.groovy", module = "groovy-all")
        exclude(group = "org.python", module = "jython-standalone")
    }
    testImplementation(libs.junit4)
    testImplementation(libs.spring.test)
    testImplementation(libs.assertj.core)
    testImplementation(libs.ant)
    testImplementation(libs.commons.beanutils)
    testImplementation(libs.hibernate.envers)
    testImplementation(libs.hibernate.ehcache)
    testImplementation(libs.h2)
    testImplementation(libs.slf4j.simple)
    testImplementation("javax.inject:javax.inject:1")
    testImplementation("jakarta.inject:jakarta.inject-api:1.0.3")
}
