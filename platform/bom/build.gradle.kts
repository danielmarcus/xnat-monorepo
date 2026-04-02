plugins {
    `java-platform`
}

group = "org.nrg"
version = libs.versions.xnat.get()

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        // === Base libraries ===
        api(project(":libs:transaction"))
        api(project(":libs:extattr"))
        api(project(":libs:test"))
        api(project(":libs:mail"))
        api(project(":libs:notify"))
        api(project(":libs:prefs"))
        api(project(":libs:config"))
        api(project(":libs:automation"))
        api(project(":libs:framework"))

        // === Data / Persistence ===
        api(project(":libs:dicomtools"))
        api(project(":libs:xdat"))
        api(project(":libs:spawner"))

        // === DICOM libraries ===
        api(project(":libs:dicom-edit4"))
        api(project(":libs:dicom-edit6"))
        api(project(":libs:dicom-image-utils"))
        api(project(":libs:ecat4xnat"))
        api(project(":libs:session-builders"))
        api(project(":libs:dicom-xnat:dicom-xnat-sop"))
        api(project(":libs:dicom-xnat:dicom-xnat-util"))
        api(project(":libs:dicom-xnat:dicom-xnat-mx"))
        api(project(":libs:prearc-importer"))

        // === Build tools ===
        api(project(":build-tools:xnat-data-models"))

        // === Application ===
        api(project(":apps:web"))
    }
}
