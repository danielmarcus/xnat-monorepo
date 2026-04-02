plugins {
    id("xnat-java-library")
    id("org.nrg.xdat.build.xdat-data-builder")
}

group = "org.nrg.xnat"
version = libs.versions.xnat.get()
description = "Contains the core data types for XNAT"

// Generated sources from the xdat-data-builder plugin
sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("xnat-generated/src/main/java"))
        }
        resources {
            srcDir(layout.buildDirectory.dir("xnat-generated/src/main/resources"))
        }
    }
}

configurations.configureEach {
    exclude(group = "commons-logging")
    exclude(group = "log4j", module = "log4j")
    exclude(module = "slf4j-simple")
    exclude(module = "slf4j-log4j12")
}

dependencies {
    // --- Shared API classes (replaces build-tools:web-stubs) ---
    compileOnly(project(":libs:xnat-api"))

    // --- Internal modules (non-transitive where noted) ---
    implementation(project(":libs:xdat")) {
        isTransitive = false
    }
    implementation(project(":libs:notify")) {
        isTransitive = false
    }
    implementation(project(":libs:mail")) {
        isTransitive = false
    }
    implementation(project(":libs:automation")) {
        isTransitive = false
    }
    implementation(project(":libs:config")) {
        isTransitive = false
    }
    implementation(project(":libs:prefs")) {
        isTransitive = false
    }
    implementation(project(":libs:transaction")) {
        isTransitive = false
    }
    implementation(project(":libs:framework")) {
        isTransitive = false
    }

    // --- Spring ---
    implementation(libs.spring.context)
    implementation(libs.spring.web)
    implementation(libs.spring.jdbc)
    implementation(libs.spring.security.core)

    // --- Hibernate ---
    implementation(libs.hibernate.core)

    // --- Jackson ---
    implementation(libs.jackson.databind)

    // --- Reactor ---
    implementation("io.projectreactor:reactor-bus:2.0.8.RELEASE")

    // --- Utilities ---
    implementation(libs.commons.lang3)
    implementation(libs.guava)
    implementation(libs.reflections)
    implementation(libs.restlet)
    implementation(libs.slf4j.api)
    implementation(libs.jul.to.slf4j)
    implementation(libs.log4j.over.slf4j)

    // --- Legacy deps (from XNAT Artifactory) ---
    implementation("turbine:turbine:2.3.3") {
        isTransitive = false
    }
    implementation("org.apache.velocity:velocity:1.7") {
        isTransitive = false
    }
    implementation("ecs:ecs:1.4.2")

    // --- Compile-only ---
    compileOnly(libs.javax.servlet.api)
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    // --- Test ---
    testImplementation(libs.junit4)
    testImplementation(libs.spring.test)
}

tasks.withType<Jar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
