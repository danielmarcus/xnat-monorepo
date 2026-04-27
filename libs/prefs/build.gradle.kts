plugins {
    id("xnat-java-library")
}

group = "org.nrg"
version = libs.versions.xnat.get()

dependencies {
    api(project(":libs:framework"))

    // --- Cache ---
    implementation(libs.ehcache3)
    implementation(libs.redisson)

    // --- Apache Commons ---
    implementation(libs.commons.beanutils)
    implementation(libs.commons.configuration2)
    implementation(libs.commons.lang3)

    // --- Utilities ---
    implementation(libs.javassist)
    implementation(libs.jackson.databind)

    // --- Spring ---
    implementation(libs.spring.jdbc)
    implementation(libs.spring.context)

    // --- Logging ---
    implementation(libs.slf4j.api)

    // --- JDK 21: javax.annotation for @PostConstruct ---
    implementation("javax.annotation:javax.annotation-api:1.3.2")

    // --- Compile-only ---
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    // --- Test ---
    // libs:test exposes org.nrg.test.utils.{TestBeans,TestFileUtils} that
    // DefaultResolverConfiguration imports.
    testImplementation(project(":libs:test"))
    testImplementation(libs.spring.test)
    testImplementation(libs.spring.context.support)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.h2)
    testImplementation(libs.logback.classic)
    testImplementation(libs.postgresql)
    testImplementation(libs.cglib)
    testImplementation("com.github.sbrannen:spring-test-junit5:1.5.0")
}
