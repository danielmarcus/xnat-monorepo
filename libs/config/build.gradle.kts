plugins {
    id("xnat-java-library")
}

group = "org.nrg"
version = libs.versions.xnat.get()

dependencies {
    api(project(":libs:prefs"))
    api(project(":libs:framework"))

    // --- Spring ---
    implementation(libs.spring.context)
    implementation(libs.spring.orm)

    // --- Hibernate ---
    implementation(libs.hibernate.core)

    // --- Apache Commons ---
    implementation(libs.commons.lang3)
    implementation(libs.commons.beanutils)

    // --- Utilities ---
    implementation(libs.cglib)
    implementation(libs.javassist)
    implementation(libs.guava)
    implementation(libs.jackson.databind)

    // --- Logging ---
    implementation(libs.slf4j.api)

    // --- Servlet ---
    compileOnly(libs.javax.servlet.api)

    // --- JDK 21: javax.annotation for @PostConstruct ---
    implementation("javax.annotation:javax.annotation-api:1.3.2")

    // --- Test ---
    // libs:test exposes org.nrg.test.utils.{TestBeans,TestFileUtils} that
    // NrgConfigTestConfiguration imports.
    testImplementation(project(":libs:test"))
    testImplementation(libs.junit4)
    testImplementation(libs.spring.test)
    testImplementation(libs.assertj.core)
    testImplementation(libs.h2)
    testImplementation(libs.postgresql)
    testImplementation(libs.commons.io)
    testImplementation(libs.slf4j.simple)
}

// ConfigPlatformTests.testLargeConfigFile constructs a String of
// MAX_FILE_LENGTH + 1 chars (~2 GB heap, since Java chars are 2 bytes)
// to exercise the size-cap check in DefaultConfigService. The convention
// plugin's default maxHeapSize=2g leaves no headroom, OOM. Override
// just for this module.
tasks.withType<Test>().configureEach {
    maxHeapSize = "4g"
}
