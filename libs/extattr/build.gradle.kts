plugins {
    id("xnat-java-library")
}

group = "org.nrg"
version = libs.versions.xnat.get()

dependencies {
    implementation(libs.commons.lang3)
    implementation(libs.guava)
    implementation(libs.slf4j.api)

    // libs/extattr ships a small set of *Test classes that import org.junit.*
    // (Test, Assert) — they're plain JUnit 4 unit tests with no Spring or
    // Hibernate context. Add the JUnit test dep so the test compile finds them.
    testImplementation(libs.junit4)
}
