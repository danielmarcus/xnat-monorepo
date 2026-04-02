plugins {
    id("xnat-java-library")
}

group = "org.nrg.xnat"
version = libs.versions.xnat.get()

dependencies {
    // --- Internal modules (non-transitive where noted) ---
    implementation(project(":libs:xdat")) {
        isTransitive = false
    }
    implementation(project(":libs:framework")) {
        isTransitive = false
    }
    api(project(":libs:config"))

    // --- Spring ---
    implementation(libs.spring.webmvc)
    implementation(libs.spring.context)
    implementation(libs.spring.security.core)

    // --- Swagger ---
    implementation(libs.swagger.springmvc)
    implementation(libs.swagger.ui)

    // --- Jackson ---
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.dataformat.xml)

    // --- Scripting ---
    implementation(libs.groovy.all)

    // --- Database ---
    implementation(libs.postgresql)

    // --- Servlet ---
    compileOnly(libs.javax.servlet.api)

    // --- Utilities ---
    implementation(libs.slf4j.api)
    implementation(libs.commons.lang3)

    // --- Test ---
    testImplementation(project(":libs:test"))
    testImplementation(libs.junit4)
    testImplementation(libs.spring.test)
    testImplementation(libs.assertj.core)
    testImplementation(libs.h2)
    testImplementation("org.hamcrest:hamcrest-library:2.2")
}
