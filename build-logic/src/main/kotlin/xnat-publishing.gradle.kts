/**
 * Convention plugin for publishing XNAT library artifacts to Artifactory.
 *
 * Applying this plugin:
 *  - Activates the maven-publish plugin
 *  - Publishes a MavenPublication named "mavenJava" that includes the
 *    production JAR, sources JAR, and Javadoc JAR
 *  - Populates standard POM metadata (licenses, SCM, developers)
 *  - Targets the XNAT Artifactory release or snapshot repository based
 *    on whether the project version ends with "-SNAPSHOT"
 *
 * Required gradle.properties keys (or equivalent environment variables):
 *   xnatArtifactoryUser     / XNAT_ARTIFACTORY_USER
 *   xnatArtifactoryPassword / XNAT_ARTIFACTORY_PASSWORD
 */

plugins {
    `maven-publish`
}

// ---------------------------------------------------------------------------
// Helper – resolve credentials with a fallback to environment variables
// ---------------------------------------------------------------------------

fun resolveProperty(gradleKey: String, envKey: String): String? =
    providers.gradleProperty(gradleKey).orNull
        ?: providers.environmentVariable(envKey).orNull

val artifactoryUser     = resolveProperty("xnatArtifactoryUser",     "XNAT_ARTIFACTORY_USER")
val artifactoryPassword = resolveProperty("xnatArtifactoryPassword", "XNAT_ARTIFACTORY_PASSWORD")

// ---------------------------------------------------------------------------
// Repository URLs
// ---------------------------------------------------------------------------

val releaseRepoUrl  = uri("https://nrgxnat.jfrog.io/nrgxnat/libs-release-local")
val snapshotRepoUrl = uri("https://nrgxnat.jfrog.io/nrgxnat/libs-snapshot-local")

fun isSnapshot() = project.version.toString().endsWith("-SNAPSHOT")

// ---------------------------------------------------------------------------
// Publication
// ---------------------------------------------------------------------------

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            // Wire up the standard Java components (jar, sources, javadoc).
            // The java or java-library plugin must be applied before this
            // convention plugin so the "java" software component exists.
            from(components["java"])

            groupId    = project.group.toString()
            artifactId = project.name
            version    = project.version.toString()

            pom {
                name.set(project.name)
                description.set(
                    project.description ?: "XNAT module: ${project.name}"
                )
                url.set("https://www.xnat.org")

                licenses {
                    license {
                        name.set("Simplified BSD License")
                        url.set(
                            "https://nrg.wustl.edu/software/license-agreement/"
                        )
                        distribution.set("repo")
                    }
                }

                organization {
                    name.set("Neuroinformatics Research Group, Washington University in St. Louis")
                    url.set("https://nrg.wustl.edu")
                }

                developers {
                    developer {
                        id.set("nrg")
                        name.set("NRG Development Team")
                        organization.set("Washington University in St. Louis")
                        organizationUrl.set("https://nrg.wustl.edu")
                    }
                }

                scm {
                    url.set("https://github.com/NrgXnat/xnat")
                    connection.set("scm:git:https://github.com/NrgXnat/xnat.git")
                    developerConnection.set("scm:git:git@github.com:NrgXnat/xnat.git")
                }

                issueManagement {
                    system.set("Jira")
                    url.set("https://issues.xnat.org")
                }
            }
        }
    }

    repositories {
        maven {
            name = "xnatArtifactory"
            url  = if (isSnapshot()) snapshotRepoUrl else releaseRepoUrl
            credentials {
                username = artifactoryUser
                password = artifactoryPassword
            }
        }
    }
}

// Ensure sources and javadoc tasks run before publishing so the artifacts
// are always present when the publish lifecycle fires.
tasks.withType<PublishToMavenRepository>().configureEach {
    dependsOn(tasks.withType<Jar>())
}
