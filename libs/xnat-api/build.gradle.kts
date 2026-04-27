plugins {
    id("xnat-java-library")
}

group = "org.nrg.xnat"
version = libs.versions.xnat.get()
description = "Shared API classes for the XNAT data model circular dependency"

// Internal-only sourceSet for typed-stub interfaces that mirror xnat-data-models
// codegen FQNs (e.g. org.nrg.xdat.model.XnatResourcecatalogI). These stubs let
// xnat-api/main compile typed CatalogUtils / scan-type signatures whose
// bytecode descriptors must match the runtime codegen versions. They MUST NOT
// be exposed to downstream consumers (apps/web sees the codegen versions from
// xnat-data-models; if it also saw these empty stubs, the compiler would pick
// the wrong one and report missing inherited methods). Output goes to a
// separate classes dir, so xnat-api's main classes-dir variant excludes them.
// See ADR 0008.
val stubs by sourceSets.creating {
    java.srcDir("src/stubs/java")
}

tasks.named<JavaCompile>("compileJava") {
    classpath += stubs.output
}

dependencies {
    api(project(":libs:xdat"))
    api(project(":libs:framework"))
    api(project(":libs:config"))
    // NOTE: No dependency on xnat-data-models to avoid circular dependency.
    // Typed-stub interfaces in src/stubs/java provide compile-time FQNs for
    // codegen types so descriptors match at runtime; see comment above.

    // Restlet (needed by ClientException, ServerException, facade types)
    implementation(libs.restlet)

    // Turbine + Velocity (needed by Screen classes)
    implementation("turbine:turbine:2.3.3") {
        isTransitive = false
    }
    implementation("org.apache.velocity:velocity:1.7") {
        isTransitive = false
    }

    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.log4j.over.slf4j)

    // Utilities
    implementation(libs.commons.lang3)
    implementation(libs.guava)

    // Spring (needed by XnatPipelineLauncher, PipelineLaunchParameters)
    implementation(libs.spring.context)

    // Servlet API (needed by turbine RunData)
    compileOnly(libs.javax.servlet.api)
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
}

// Exclude facade classes from the JAR that have real implementations in apps/web.
// These facades exist only for compile-time resolution in xnat-data-models.
// At runtime, apps/web (or generated xnat-data-models code) provides the real
// classes; the stubs MUST be kept off the runtime classpath or descriptor
// mismatches between (Object,…) stubs and (TypedI,…) impls will cause
// NoSuchMethodError. See ADR 0008.
tasks.named<Jar>("jar") {
    exclude("org/nrg/xnat/utils/CatalogUtils.class")
    exclude("org/nrg/xnat/utils/CatalogUtils\$*.class")
    exclude("org/nrg/xnat/helpers/prearchive/PrearcUtils.class")
    exclude("org/nrg/xnat/helpers/prearchive/PrearcUtils\$*.class")
    exclude("org/nrg/xnat/turbine/utils/XNATUtils.class")
    exclude("org/nrg/xnat/turbine/utils/XNATUtils\$*.class")
    exclude("org/nrg/xnat/helpers/merge/ProjectAnonymizer.class")
    exclude("org/nrg/xnat/helpers/merge/ProjectAnonymizer\$*.class")
    exclude("org/nrg/xnat/restlet/resources/ScriptTriggerTemplateResource.class")
    exclude("org/nrg/xnat/ajax/writer/JSONWriter.class")
    // Scan-type facades — typed against XnatImagescandataI (see ADR 0008)
    exclude("org/nrg/xnat/helpers/scanType/ScanTypeMappingI.class")
    exclude("org/nrg/xnat/helpers/scanType/AbstractScanTypeMapping.class")
    exclude("org/nrg/xnat/helpers/scanType/AbstractScanTypeMapping\$*.class")
    exclude("org/nrg/xnat/helpers/scanType/ImageScanTypeMapping.class")
    exclude("org/nrg/xnat/helpers/scanType/ImageScanTypeMapping\$*.class")
    // (Typed-stub interfaces under org.nrg.xdat.model live in the `stubs`
    // sourceSet, so they never enter main's output and don't need a JAR
    // exclusion. See sourceSet config above and ADR 0008.)
    // Other facades — some still use Object params (Phase 2 cleanup)
    exclude("org/nrg/xnat/turbine/utils/ArchivableItem.class")
    exclude("org/nrg/xnat/turbine/utils/CatalogSet.class")
    exclude("org/nrg/xnat/turbine/utils/CatalogSet\$*.class")
    exclude("org/nrg/xdat/om/base/MoveableI.class")
    exclude("org/nrg/xdat/om/base/MoverMaker.class")
    exclude("org/nrg/xdat/om/base/MoverMaker\$*.class")
    exclude("org/nrg/xnat/turbine/modules/screens/EditSubjectAssessorScreen.class")
    exclude("org/nrg/xnat/turbine/modules/screens/EditImageAssessorScreen.class")
    exclude("org/nrg/xnat/utils/WorkflowUtils.class")
    exclude("org/nrg/xnat/utils/WorkflowUtils\$*.class")
}
