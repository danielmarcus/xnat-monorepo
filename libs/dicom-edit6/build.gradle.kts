plugins {
    id("xnat-java-library")
}

group = "org.nrg.dicom"
version = libs.versions.xnat.get()

val antlr4 by configurations.creating

dependencies {
    // --- ANTLR4 tool for code generation ---
    antlr4(libs.antlr4.tool)

    // --- Internal modules ---
    api(project(":libs:framework")) {
        exclude(group = "org.slf4j", module = "slf4j-simple")
    }

    // --- ANTLR4 runtime ---
    implementation(libs.antlr4.runtime)

    // --- DICOM ---
    implementation(libs.dcm4che5.core)
    implementation(libs.dcm4che5.net)

    // --- External: mizer (from XNAT Artifactory) ---
    implementation("org.nrg.dicom:mizer:${project.version}")

    // --- Imaging ---
    implementation("com.dclunie:pixelmed:nrg-20200327")
    runtimeOnly(libs.pixelmed.codec)

    // --- Spring ---
    implementation(libs.spring.core)
    implementation(libs.spring.context)
    implementation(libs.spring.beans)

    // --- Utilities ---
    implementation(libs.guava)
    implementation(libs.commons.lang3)
    compileOnly("org.kohsuke.metainf-services:metainf-services:1.11")
    annotationProcessor("org.kohsuke.metainf-services:metainf-services:1.11")

    // --- Logging ---
    implementation(libs.slf4j.api)

    // --- Test ---
    // libs:test exposes org.nrg.test.workers.resources.ResourceManager that
    // every test in this module imports for fixture resource loading.
    testImplementation(project(":libs:test"))
    testImplementation(libs.junit4)
    testImplementation(libs.spring.test)
    testImplementation("net.imagej:ij:1.54f")
    testImplementation("javax.validation:validation-api:1.1.0.Final")
}

// Custom ANTLR4 task that processes grammars in the right order
// (lexer first, then parser, then imported grammars)
val generateGrammarSource by tasks.registering(JavaExec::class) {
    val grammarDir = file("src/main/antlr/org/nrg/dicom/dicomedit")
    val outputDir = layout.buildDirectory.dir("generated-src/antlr/main/org/nrg/dicom/dicomedit").get().asFile

    inputs.dir(grammarDir)
    outputs.dir(outputDir)

    classpath = antlr4
    mainClass.set("org.antlr.v4.Tool")

    doFirst {
        outputDir.mkdirs()
    }

    // Process grammars: lexer-related first, then parser
    args(
        "-visitor", "-listener",
        "-o", outputDir.absolutePath,
        "-lib", outputDir.absolutePath,
        "-package", "org.nrg.dicom.dicomedit",
        "${grammarDir}/Comment.g4",
        "${grammarDir}/TagPath.g4",
        "${grammarDir}/DE6Lexer.g4",
        "${grammarDir}/DE6Parser.g4"
    )
}

sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("generated-src/antlr/main"))
        }
    }
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(generateGrammarSource)
}

tasks.named<Jar>("sourcesJar") {
    dependsOn(generateGrammarSource)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named("javadocJar") {
    dependsOn(generateGrammarSource)
}

// PixelmedPixelEditHandlerTest.multiframe_evle_rgb_8bit constructs a
// BasePixelDataValidator over a multiframe RGB-8 DICOM, which loads the
// full pixel array into memory in createPixelValue. With the convention
// plugin's default maxHeapSize=2g it OOMs at validator construction.
// Multiframe RGB images can run several hundred MB; bump to 4g for this
// module so the validator has room. Mirrors the libs/config heap bump
// from PR #24 — same shape, same rationale.
tasks.withType<Test>().configureEach {
    maxHeapSize = "4g"
}
