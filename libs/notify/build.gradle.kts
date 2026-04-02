plugins {
    id("xnat-java-library")
}

group = "org.nrg"
version = libs.versions.xnat.get()

dependencies {
    api(project(":libs:mail"))
    api(project(":libs:framework"))

    // --- Spring ---
    implementation(libs.spring.context)
    implementation(libs.spring.orm)

    // --- Hibernate ---
    implementation(libs.hibernate.core)

    // --- Jackson ---
    implementation(libs.jackson.databind)

    // --- Apache Commons ---
    implementation(libs.commons.lang3)

    // --- Utilities ---
    implementation(libs.cglib)
    implementation(libs.javassist)
    implementation("com.sun.activation:javax.activation:1.2.0")

    // --- JDK 21: javax.mail, javax.annotation, javax.inject ---
    implementation("com.sun.mail:javax.mail:1.6.2")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    implementation("javax.inject:javax.inject:1")

    // --- Logging ---
    implementation(libs.slf4j.api)

    // --- Test ---
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.spring.test)
    testImplementation(libs.assertj.core)
    testImplementation(libs.h2)
    testImplementation(libs.ehcache3)
    testImplementation(libs.logback.classic)
    testImplementation("com.github.sbrannen:spring-test-junit5:1.5.0")
}
