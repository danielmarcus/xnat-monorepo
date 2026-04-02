plugins {
    id("xnat-java-library")
    antlr
}

group = "org.nrg.dicom"
version = libs.versions.xnat.get()

// ANTLR3 grammars live in src/main/antlr3/
sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("generated-src/antlr/main"))
        }
    }
}

dependencies {
    // --- ANTLR3 tool for code generation ---
    antlr(libs.antlr3.tool)

    // --- Internal modules ---
    api(project(":libs:dicomtools"))
    api(project(":libs:framework")) {
        isTransitive = false
    }

    // --- ANTLR3 runtime ---
    implementation(libs.antlr3.runtime)

    // --- DICOM ---
    implementation(libs.dcm4che2.core)
    implementation(libs.dcm4che5.core)
    implementation(libs.dcm4che5.net)

    // --- External: mizer (from XNAT Artifactory) ---
    implementation("org.nrg.dicom:mizer:${project.version}")

    // --- Utilities ---
    implementation(libs.reflections)
    implementation(libs.guava)
    implementation("org.json:json:20231013")
    implementation("org.kohsuke.metainf-services:metainf-services:1.11")

    // --- Spring ---
    compileOnly(libs.spring.web)

    // --- Compile-only ---
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    // --- Logging ---
    implementation(libs.slf4j.api)

    // --- Test ---
    testImplementation(libs.junit4)
    testImplementation(libs.spring.test)
    testImplementation(libs.log4j.over.slf4j)
    testImplementation("easymock:easymock:2.0")
}

tasks.generateGrammarSource {
    // ANTLR3 grammars are in src/main/antlr/org/nrg/dcm/edit/
    outputDirectory = layout.buildDirectory.dir("generated-src/antlr/main/org/nrg/dcm/edit").get().asFile
    arguments = arguments + listOf("-lib", file("src/main/antlr/org/nrg/dcm/edit").absolutePath)
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(tasks.generateGrammarSource)
}

tasks.named<Jar>("sourcesJar") {
    dependsOn(tasks.generateGrammarSource)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named("javadocJar") {
    dependsOn(tasks.generateGrammarSource)
}
