plugins {
    `java-library`
}

group = "org.nrg.xnat"
version = "stub"
description = "Compilation stubs for the xnat-data-models <-> web circular dependency"

configurations.configureEach {
    exclude(group = "commons-logging")
    exclude(group = "log4j", module = "log4j")
    exclude(module = "slf4j-simple")
    exclude(module = "slf4j-log4j12")
}

dependencies {
    // Internal modules needed for type references in stubs
    implementation(project(":libs:xdat")) {
        isTransitive = false
    }
    implementation(project(":libs:framework")) {
        isTransitive = false
    }
    implementation(project(":libs:transaction")) {
        isTransitive = false
    }

    // Turbine & Velocity (needed by generated Screen classes)
    implementation("turbine:turbine:2.3.3") {
        isTransitive = false
    }
    implementation("org.apache.velocity:velocity:1.7") {
        isTransitive = false
    }

    // Restlet (for org.restlet.data.Status used in ClientException)
    implementation(libs.restlet)

    // Servlet API (needed by turbine RunData)
    compileOnly(libs.javax.servlet.api)

    // Logging (generated Screen classes use org.apache.log4j.Logger)
    implementation(libs.log4j.over.slf4j)
    implementation(libs.slf4j.api)

    // Spring (for framework references)
    implementation(libs.spring.context)
}
