pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven {
            name = "xnatRelease"
            url = uri("https://nrgxnat.jfrog.io/nrgxnat/libs-release")
        }
        maven {
            name = "xnatSnapshot"
            url = uri("https://nrgxnat.jfrog.io/nrgxnat/libs-snapshot")
        }
    }
}

rootProject.name = "xnat-data-builder"
