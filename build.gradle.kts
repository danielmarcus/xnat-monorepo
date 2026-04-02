plugins {
    base
}

group = "org.nrg"
version = libs.versions.xnat.get()

tasks.register("cleanAll") {
    description = "Clean all subprojects"
    dependsOn(subprojects.map { it.tasks.named("clean") })
}

// Convenience task to build everything
tasks.register("buildAll") {
    description = "Build all subprojects"
    dependsOn(subprojects.map { it.tasks.named("build") })
}
