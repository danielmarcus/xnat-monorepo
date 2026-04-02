# XNAT Data Models #

This library uses the [XDAT data builder plugin](https://bitbucket.org/xnatdev/xdat-data-builder) to build the core XNAT data models from the data-type definitions (schema) and provided resources.  It contains the schema and XFT resources for the core XNAT data types.

# Building #

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

