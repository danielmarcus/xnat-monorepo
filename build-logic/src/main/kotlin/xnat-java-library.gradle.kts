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
        force("io.projectreactor:reactor-core:2.0.8.RELEASE")
        // XNAT uses logback-classic 1.2.x which requires SLF4J 1.7.x.
        // Various transitive deps pull in SLF4J 2.x which is incompatible.
        force("org.slf4j:slf4j-api:1.7.36")
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
// Test classpath additions shared by every library module
//
// Spring Test's WebDelegatingSmartContextLoader is on the runtime classpath
// of every Spring-based test context (it's chosen at startup even when no
// @WebAppConfiguration is present). It needs javax.servlet.ServletContext
// to load. Modules that declare `compileOnly(libs.javax.servlet.api)` for
// their main sources still don't have it on the test runtime classpath,
// which produces NoClassDefFoundError at test-context bootstrap.
//
// Adding the servlet API at testRuntimeOnly here means every module gets
// the runtime jar without having to repeat the declaration. It costs ~200KB
// of memory in the test JVM and zero compile-time impact.
// ---------------------------------------------------------------------------

dependencies {
    "testRuntimeOnly"("javax.servlet:javax.servlet-api:3.1.0")
    // Many modules declare only junit4 on testImplementation. The convention
    // plugin's useJUnitPlatform() requires a Platform engine on the runtime
    // classpath; without one the test JVM aborts with
    //   PreconditionViolationException: Cannot create Launcher without at
    //   least one TestEngine; consider adding an engine implementation JAR
    // junit-vintage-engine bridges JUnit 4 onto Platform; jupiter-engine
    // covers @Test (JUnit 5) modules. Both are safe to declare in parallel.
    "testRuntimeOnly"("org.junit.vintage:junit-vintage-engine:5.8.1")
    "testRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine:5.8.1")
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
