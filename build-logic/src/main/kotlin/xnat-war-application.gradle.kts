/**
 * Convention plugin for the XNAT WAR application module (xnat-web).
 *
 * Applying this plugin configures:
 *  - The `war` plugin for WAR packaging
 *  - Lombok annotation processing via io.freefair.lombok
 *  - Java 21 toolchain (compile and runtime)
 *  - UTF-8 source encoding and compiler lint warnings
 *  - JUnit Platform test runner with Java 21 --add-opens args
 *  - Standard repository set (Maven Central + XNAT Artifactory)
 *  - Reproducible WAR/JAR manifests
 */

plugins {
    `java-library`
    war
    id("io.freefair.lombok")
}

// Repositories are declared centrally in settings.gradle.kts

// ---------------------------------------------------------------------------
// Dependency resolution strategy
// ---------------------------------------------------------------------------

configurations.all {
    resolutionStrategy {
        force("io.projectreactor:reactor-core:2.0.8.RELEASE")
        force("org.slf4j:slf4j-api:1.7.36")
    }
}

// ---------------------------------------------------------------------------
// Java toolchain – compile & run on JDK 21
// ---------------------------------------------------------------------------

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// ---------------------------------------------------------------------------
// Compiler options
// ---------------------------------------------------------------------------

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(
        listOf(
            "-Xlint:all",
            "-Xlint:-processing",  // suppress Lombok/annotation-processor noise
            "-Xlint:-serial",
            "-parameters",         // retain parameter names for Spring MVC / Jackson
        )
    )
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:none", "-quiet")
    }
}

// ---------------------------------------------------------------------------
// WAR packaging
// ---------------------------------------------------------------------------

tasks.named<War>("war") {
    // Defensive cleanup at configuration time: if the destination
    // archiveFile is a directory rather than a file, Gradle rejects the
    // task during task-graph validation BEFORE doFirst runs:
    //   "Cannot write a file to a location pointing at a directory."
    // and the user is left wondering what to do. The directory shape
    // typically comes from Docker bind-mount stub-creation when the
    // host file didn't exist at compose-up time (the docker-compose
    // files now use create_host_path: false to prevent NEW occurrences,
    // but existing checkouts may already have one). Detect at configure
    // time and recursively delete — the war task is going to overwrite
    // this path with the freshly built WAR anyway.
    run {
        val out = archiveFile.get().asFile
        if (out.isDirectory) {
            logger.warn("Removing phantom directory at WAR output path: ${out.absolutePath}")
            out.deleteRecursively()
        }
    }

    // Exclude Tomcat's servlet API from WEB-INF/lib – the container provides it.
    classpath(
        configurations["runtimeClasspath"].filter { file ->
            !file.name.startsWith("jakarta.servlet-api") &&
            !file.name.startsWith("tomcat-embed")
        }
    )

    manifest {
        val attrs = mutableMapOf<String, Any?>(
            "Implementation-Title"   to project.name,
            "Implementation-Version" to project.version,
            "Implementation-Vendor"  to "Washington University in St. Louis / NRG",
            "Built-By"               to System.getProperty("user.name"),
            "Build-Jdk"              to System.getProperty("java.version"),
            "Created-By"             to "Gradle ${gradle.gradleVersion}",
            "Main-Class"             to "org.nrg.xnat.XnatApplication",
        )
        // XNAT-required manifest attributes (XnatAppInfo reads these at startup)
        attrs["Build-Date"]             = providers.exec { commandLine("date", "+%Y-%m-%d %H:%M:%S") }.standardOutput.asText.get().trim()
        attrs["Implementation-Sha"]     = project.findProperty("gitSha") ?: "unknown"
        attrs["Implementation-Branch"]  = project.findProperty("gitBranch") ?: "main"
        attrs["Implementation-Commit"]  = project.findProperty("gitCommit") ?: "unknown"
        attrs["Implementation-Dirty"]   = "false"
        attributes(attrs)
    }
}

// Keep a thin executable JAR alongside the WAR for Spring Boot's embedded
// container launch mode (`java -jar xnat-web.jar`).
tasks.named<Jar>("jar") {
    archiveClassifier.set("plain")
    manifest {
        attributes(
            "Implementation-Title"   to project.name,
            "Implementation-Version" to project.version,
            "Implementation-Vendor"  to "Washington University in St. Louis / NRG",
            "Built-By"               to System.getProperty("user.name"),
            "Build-Jdk"              to System.getProperty("java.version"),
            "Created-By"             to "Gradle ${gradle.gradleVersion}",
        )
    }
}

// ---------------------------------------------------------------------------
// Test classpath shared with every module — see xnat-java-library.gradle.kts
// for rationale.  Spring Test bootstrap loads WebDelegatingSmartContextLoader
// which needs javax.servlet.ServletContext on the test runtime classpath.
// ---------------------------------------------------------------------------

dependencies {
    "testRuntimeOnly"("javax.servlet:javax.servlet-api:3.1.0")
    // See xnat-java-library.gradle.kts for rationale.
    "testRuntimeOnly"("org.junit.vintage:junit-vintage-engine:5.8.1")
    "testRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

// ---------------------------------------------------------------------------
// Test configuration
// ---------------------------------------------------------------------------

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    systemProperty("file.encoding", "UTF-8")

    // Java 21 strong encapsulation workarounds for reflection-heavy frameworks.
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        // Spring's ReflectionUtils needs access to private fields on Java 21.
        "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
    )

    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}
