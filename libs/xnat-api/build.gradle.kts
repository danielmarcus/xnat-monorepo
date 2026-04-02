plugins {
    id("xnat-java-library")
}

group = "org.nrg.xnat"
version = libs.versions.xnat.get()
description = "Shared API classes for the XNAT data model circular dependency"

dependencies {
    api(project(":libs:xdat"))
    api(project(":libs:framework"))
    api(project(":libs:config"))
    // NOTE: No dependency on xnat-data-models to avoid circular dependency.
    // Facades use Object types / unchecked generics for xnat-data-models types.

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
// At runtime, apps/web provides the real classes in WEB-INF/classes.
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
}
