/**
 * Convention plugin for all XNAT Java library modules.
 *
 * Applying this plugin to a subproject configures:
 *  - java-library with a Java 21 toolchain
 *  - Lombok annotation processing via io.freefair.lombok
 *  - UTF-8 source encoding and compiler lint warnings
 *  - JUnit Platform test runner
 *  - Standard repository set (Maven Central + XNAT Artifactory)
 *  - Reproducible JAR manifests carrying project metadata
 *  - JVM --add-opens arguments required for deep-reflection on Java 21
 */

plugins {
    `java-library`
    id("io.freefair.lombok")
}

// Repositories are declared centrally in settings.gradle.kts
// (dependencyResolutionManagement.repositoriesMode = PREFER_SETTINGS)

// ---------------------------------------------------------------------------
// Dependency resolution strategy
// ---------------------------------------------------------------------------

configurations.all {
    resolutionStrategy {
        // XNAT uses Reactor 2.x (reactor-bus, reactor-core 2.0.8.RELEASE).
        // Spring Framework 5.3.x pulls in Reactor 3.x which is API-incompatible.
        // Force reactor-core to 2.x to preserve compatibility.
        force("io.projectreactor:reactor-core:2.0.8.RELEASE")
    }
}

// ---------------------------------------------------------------------------
// Java toolchain – all modules compile & run on JDK 21
// ---------------------------------------------------------------------------

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    // Produce sources and javadoc JARs automatically; publishing plugin picks
    // these up without extra configuration.
    withSourcesJar()
    withJavadocJar()
}

// ---------------------------------------------------------------------------
// Compiler options
// ---------------------------------------------------------------------------

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(
        listOf(
            "-Xlint:all",          // enable all lint categories
            "-Xlint:-processing",  // suppress annotation-processor noise (Lombok)
            "-Xlint:-serial",      // serialVersionUID warnings add little value here
            "-parameters",         // retain parameter names for frameworks (Spring, Jackson)
        )
    )
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:none", "-quiet")
    }
    // Don't fail the build on javadoc errors (generated code often has issues)
    isFailOnError = false
}

// ---------------------------------------------------------------------------
// Test configuration
// ---------------------------------------------------------------------------

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    // Forward useful system properties into the test JVM
    systemProperty("file.encoding", "UTF-8")

    // Java 21 strong encapsulation: open packages that popular testing
    // frameworks (Mockito, Spring, etc.) access via reflection.
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
    )

    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}

// ---------------------------------------------------------------------------
// JAR manifest
// ---------------------------------------------------------------------------

tasks.named<Jar>("jar") {
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
