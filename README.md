# XNAT Data Builder Gradle Plugin #

This performs code generation and fix-ups for the [XNAT Web application build](https://bitbucket.org/xnatdev/xnat-web).

### Building the Plugin ###

You can perform a build to your local Maven repository for development purposes like this:

```bash
gradle clean jar publishToMavenLocal
```

You can perform a build deploying to the XNAT Maven repository like this:

```bash
gradle clean jar publishToMavenLocal publishMavenJavaPublicationToMavenRepository
```

Note that, for this to work, you need to have an account on the Maven repository. You also need to pass your credentials to access the repository. You can set these in the file ~/.gradle/gradle.properties:

```
repoUsername=<username>
repoPassword=<password>
```
