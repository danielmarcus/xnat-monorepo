plugins {
    id("xnat-java-library")
}

group = "org.nrg"
version = libs.versions.xnat.get()

dependencies {
    api(project(":libs:framework"))

    // --- Scripting ---
    implementation(libs.groovy.all)
    implementation(libs.jython.standalone)

    // --- GraalJS (JDK 21 JavaScript engine replacement) ---
    implementation(libs.graaljs.polyglot)
    implementation(libs.graaljs.js.language)
    implementation("org.graalvm.js:js-scriptengine:23.1.0")

    // --- Spring ---
    implementation(libs.spring.context)
    implementation(libs.spring.security.core)
    implementation(libs.spring.oxm)

    // --- Reactor ---
    implementation(libs.io.projectreactor.core)
    implementation("io.projectreactor:reactor-bus:2.0.8.RELEASE")

    // --- Hibernate ---
    implementation(libs.hibernate.core)
    implementation(libs.hibernate.envers)

    // --- Jackson ---
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)

    // --- Utilities ---
    implementation(libs.reflections)
    implementation(libs.guava)
    implementation(libs.javassist)
    implementation(libs.cglib)
    implementation(libs.commons.lang3)
    implementation(libs.slf4j.api)

    // --- JDK 21: javax.annotation for @PostConstruct ---
    implementation("javax.annotation:javax.annotation-api:1.3.2")

    // --- Servlet ---
    compileOnly(libs.javax.servlet.api)

    // --- Runtime ---
    runtimeOnly(libs.hibernate.jcache)
    runtimeOnly(libs.ehcache3)
    runtimeOnly(libs.redisson)

    // --- Test ---
    testImplementation(libs.spring.test)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.h2)
    testImplementation("com.github.sbrannen:spring-test-junit5:1.5.0")
}
