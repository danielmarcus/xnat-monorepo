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
    // Exclude Tomcat's servlet API from WEB-INF/lib – the container provides it.
    classpath(
        configurations["runtimeClasspath"].filter { file ->
            !file.name.startsWith("jakarta.servlet-api") &&
            !file.name.startsWith("tomcat-embed")
        }
    )

    manifest {
        attributes(
            "Implementation-Title"   to project.name,
            "Implementation-Version" to project.version,
            "Implementation-Vendor"  to "Washington University in St. Louis / NRG",
            "Built-By"               to System.getProperty("user.name"),
            "Build-Jdk"              to System.getProperty("java.version"),
            "Created-By"             to "Gradle ${gradle.gradleVersion}",
            "Main-Class"             to "org.nrg.xnat.XnatApplication",
        )
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
