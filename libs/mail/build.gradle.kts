plugins {
    id("xnat-java-library")
}

group = "org.nrg"
version = libs.versions.xnat.get()

dependencies {
    api(project(":libs:framework"))

    // --- Spring ---
    implementation(libs.spring.context)
    implementation(libs.spring.context.support)
    implementation(libs.spring.web)

    // --- HTTP ---
    implementation("org.apache.httpcomponents:httpclient:4.5.14")

    // --- Apache Commons ---
    implementation(libs.commons.lang3)
    implementation(libs.commons.codec)
    implementation("org.apache.commons:commons-email:1.5")

    // --- Mail ---
    implementation("javax.mail:javax.mail-api:1.6.2")
    implementation("com.sun.mail:javax.mail:1.6.2")
    implementation("javax.activation:activation:1.1.1")
    implementation("javax.inject:javax.inject:1")

    // --- Logging ---
    implementation(libs.slf4j.api)

    // --- Test ---
    testImplementation(libs.junit4)
    testImplementation(libs.mockito.core)
    testImplementation(libs.spring.test)
}
