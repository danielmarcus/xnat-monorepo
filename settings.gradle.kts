pluginManagement {
    includeBuild("build-logic")
    includeBuild("build-tools/xdat-data-builder")
    includeBuild("build-tools/xnat-data-builder")

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

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.PREFER_SETTINGS
    repositories {
        mavenCentral()
        maven {
            name = "xnatRelease"
            url = uri("https://nrgxnat.jfrog.io/nrgxnat/libs-release")
        }
        maven {
            name = "xnatSnapshot"
            url = uri("https://nrgxnat.jfrog.io/nrgxnat/libs-snapshot")
        }
        maven {
            name = "xnatExtRelease"
            url = uri("https://nrgxnat.jfrog.io/nrgxnat/ext-release-local")
        }
        maven {
            name = "dcm4che"
            url = uri("https://maven.dcm4che.org")
        }
        maven {
            name = "nroduit"
            url = uri("https://raw.githubusercontent.com/nroduit/mvn-repo/master")
        }
        maven {
            name = "jitpack"
            url = uri("https://jitpack.io")
        }
        mavenLocal()
    }
}

rootProject.name = "xnat-monorepo"

// === Platform / BOM ===
include(":platform:bom")

// === Base libraries ===
include(":libs:transaction")
include(":libs:extattr")
include(":libs:test")
include(":libs:mail")
include(":libs:notify")
include(":libs:prefs")
include(":libs:config")
include(":libs:automation")
include(":libs:framework")
include(":libs:dicomtools")
include(":libs:xdat")
include(":libs:spawner")

// === DICOM libraries ===
include(":libs:dicom-edit4")
include(":libs:dicom-edit6")
include(":libs:dicom-image-utils")
include(":libs:ecat4xnat")
include(":libs:session-builders")
include(":libs:dicom-xnat:dicom-xnat-sop")
include(":libs:dicom-xnat:dicom-xnat-util")
include(":libs:dicom-xnat:dicom-xnat-mx")
include(":libs:prearc-importer")

// === Build tools (non-plugin modules) ===
include(":build-tools:web-stubs")
include(":build-tools:xnat-data-models")

// === Application ===
include(":apps:web")
