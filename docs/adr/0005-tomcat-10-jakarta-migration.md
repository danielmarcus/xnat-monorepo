# ADR 0005: Tomcat 10 via Deploy-Time Jakarta EE Migration

**Status:** Accepted
**Date:** 2026-04-27
**Authors:** XNAT Core Team / DevOps
**Deciders:** XNAT Steering Committee

---

## Context

XNAT must run on a Servlet 5.0 (Jakarta EE 9+) container. Tomcat 9 reached
the end of its standard support window and continued use is not viable for
new deployments. The target container is **Tomcat 10.1**, which only serves
the `jakarta.*` Servlet API.

The codebase compiles against `javax.*` and is large:

| Surface | Approximate count |
|---|---|
| `import javax.servlet.*` | 207 |
| `import javax.persistence.*` | 340 |
| Other `javax.*` (validation, ws.rs, mail, …) | ~50 |

Three transitive constraints make a source-level migration genuinely
multi-day work:

1. **Restlet 1.1.10** powers every `/data/*` REST endpoint (subjects,
   experiments, prearchive, archive, resources). The 1.x line predates the
   `org.restlet:org.restlet:2.x` API redesign — there is no upgrade path
   to a Jakarta-compatible Restlet without rewriting every endpoint, and
   the 2.5+ Jakarta-native release would not be a drop-in replacement.
2. **Spring 5.3.x → Spring 6.1.x** requires bumping a chain of locked
   transitive versions (`reactor-core`, `slf4j`, `logback`) and rewriting
   every `WebSecurityConfigurerAdapter` subclass — Spring Security 6 removes
   it.
3. **Hibernate 5.6 → 6.4** changes the implicit naming strategy. Schema
   diffs against any pre-existing customer database would require an
   audit and a migration plan.

The original Phase C plan (`docs/plan-tomcat10-eks-tests.md`) called for all
three changes in a single branch. Realistic execution time and review surface
make that infeasible for the working branch.

---

## Decision

Land **Tomcat 10.1 + Jakarta EE 9** without source changes. Use Apache's
[`jakartaee-migration`](https://tomcat.apache.org/migration-10.html) tool
(version 1.0.10, bundled in `tomcat:10.1` base images at
`/usr/local/tomcat/lib/jakartaee-migration-1.0.10-shaded.jar`) to rewrite
`javax.*` → `jakarta.*` references in the WAR **at container start**, before
Tomcat scans it.

Implementation is the new entrypoint
[`deploy/docker-compose/xnat/jakarta-migrate-and-start.sh`](../../deploy/docker-compose/xnat/jakarta-migrate-and-start.sh):

1. Detect input WAR location:
   - `/opt/xnat-input/ROOT.war` (volume-mounted, docker-compose path), or
   - `/usr/local/tomcat/webapps/ROOT.war` (baked in by `Dockerfile.k8s`).
2. Run `java -jar jakartaee-migration-1.0.10-shaded.jar -profile=EE` against
   it, writing a transformed WAR to `webapps/ROOT.war`.
3. Sentinel-touch `webapps/.jakarta-migrated` so a container restart skips
   the rewrite (idempotent in effect; just slow on every cold boot).
4. Chain into `wait-for-postgres.sh` + `catalina.sh run` as before.

The migration takes ~20 seconds for a 230 MB WAR on a typical CI runner.
First-class-load is otherwise unchanged.

`web.xml` is bumped to schema 6.0 (`https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd`)
so Tomcat 10 doesn't reject the deployment for schema-mismatch.

---

## Alternatives Considered

### Source-level migration via OpenRewrite

The OpenRewrite recipe `org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta`
handles the bulk of `javax.*` → `jakarta.*` rewrites at the source level.

**Deferred (not rejected) because:**

1. The recipe runs against modules whose dependencies (Spring, Hibernate)
   are still `javax.*`-typed. Mid-migration the build is broken in many
   ways at once.
2. Combined with the Spring 6 / Hibernate 6 / Spring Security 6 bumps
   the source rewrite needs to land alongside, the change set touches
   nearly every file in `libs/xdat`, `libs/framework`, `libs/xnat-api`,
   and `apps/web` — a review surface measured in thousands of lines.
3. The deploy-time tool achieves the runtime goal (Tomcat 10 deploys)
   without any of that risk.

OpenRewrite remains the right answer for Phase C.2, where the Spring 6
bump forces source-level Jakarta either way.

### Runtime classloader transformer (`JakartaTransformerListener`)

The original Phase C plan proposed a `<Listener>` element in
`META-INF/context.xml` that rewrote classes at load time.

**Rejected because:** No such listener exists in `jakartaee-migration 1.0.10`.
The shaded JAR ships only build/CLI/Ant entry points
(`Migration`, `MigrationCLI`, `MigrationTask`); there is no class-load-time
integration in this version. An earlier draft of this branch wired the
non-existent listener and the WAR failed to deploy with
`NoClassDefFoundError: javax/servlet/http/HttpSessionListener` because
`net.bull.javamelody.SessionListener` was loaded but the transformer never
ran. The deploy-time entrypoint approach replaced that draft.

### Stay on Tomcat 9

**Rejected because:** Tomcat 9 reached end of standard support in 2024.
Continued use accumulates security risk and pushes the migration further
into the future without bounded benefit.

### Replace Restlet with Spring MVC controllers

Restlet 1.1.10 is the bottleneck — replacing it with Spring MVC
`@RestController` classes would unblock a clean Spring 6 / Jakarta migration.

**Out of scope here:** This is genuinely weeks of work. The migration tool
sidesteps it entirely; Restlet's `javax.servlet.HttpServlet` extension gets
rewritten to `jakarta.servlet.HttpServlet` and the rest of Restlet's
internals don't care. Replacing Restlet is the right long-term answer but
shouldn't gate the Tomcat 10 cutover.

---

## Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│  xnat-web container (tomcat:10.1-jdk21-temurin)                    │
│                                                                    │
│  ENTRYPOINT: jakarta-migrate-and-start.sh                          │
│                                                                    │
│  1. /opt/xnat-input/ROOT.war   (javax.*-typed)                     │
│         │                                                          │
│         │  jakartaee-migration-1.0.10-shaded.jar                   │
│         ▼  -profile=EE                                             │
│  2. /tmp/ROOT-jakarta.war      (jakarta.*-typed)                   │
│         │                                                          │
│         │  mv                                                      │
│         ▼                                                          │
│  3. webapps/ROOT.war           (jakarta.*-typed, deployable)       │
│         │                                                          │
│         │  catalina.sh run                                         │
│         ▼                                                          │
│  4. Tomcat 10.1 deploys ROOT.war                                   │
│         │                                                          │
│         │  WebappClassLoader loads jakarta.*-typed classes         │
│         ▼                                                          │
│  5. XNAT serving on :8080                                          │
└────────────────────────────────────────────────────────────────────┘
```

Migration takes ~21 seconds for a 233 MB WAR on Apple Silicon. Sentinel
file `webapps/.jakarta-migrated` skips on subsequent boots of the same
container.

---

## Consequences

### Positive

- **Zero source changes.** Spring 5.3.39, Hibernate 5.6.15, Restlet 1.1.10,
  the forced version pins (`reactor-core 2.0.8`, `slf4j 1.7.36`) all stay
  exactly as-is. No risk of drift.
- **Single artefact pipeline.** The same WAR runs on the local Compose
  stack, the EC2 deploy, and the EKS deploy. Migration happens at deploy
  time on each target.
- **Reversible.** Removing the entrypoint and reverting the Dockerfile to
  Tomcat 9 brings everything back to pre-Phase-C.1 state.

### Negative / Accepted Trade-offs

- **+20s cold-start.** First-container boot pays the migration cost. The
  sentinel makes restarts fast, but stateless rebuilds (CI, EKS rolls)
  always pay the full ~20s. Acceptable for a deploy-time step that runs
  rarely.
- **The build still produces a `javax.*`-typed WAR.** Anyone consuming
  the WAR outside the supplied entrypoint (e.g. a custom Tomcat
  deployment) must run the migration themselves, or stay on Tomcat 9.
  `BUILD.md` should call this out — `apps/web/build/libs/web-*.war` is
  *not* a Tomcat-10-ready artefact.
- **Migration tool version pinning.** The bundled `jakartaee-migration` is
  whatever the `tomcat:10.1` image ships (currently 1.0.10). When the base
  image upgrades the tool, behaviour can change. The image tag in
  `Dockerfile` and `Dockerfile.k8s` controls this.
- **No source-level Jakarta cleanup.** Code reviewers still see `javax.*`
  imports everywhere. Static analysis tools that flag deprecated APIs will
  light up. This is a pre-condition for Phase C.2, not a long-term state.

---

## Phase C.2 (Deferred)

Source-level migration via OpenRewrite, plus Spring 6.1.x / Spring
Security 6.3.x / Hibernate 6.4.x bumps, will follow as a separate branch.
Pre-flight tasks documented under "Pre-flight notes (C.2)" in
`docs/plan-tomcat10-eks-tests.md`:

1. Drop the forced versions in
   `build-logic/src/main/kotlin/xnat-{war-application,java-library}.gradle.kts`.
2. Bump catalog `slf4j` and `logback` together.
3. Verify the Tomcat-9 build still passes.
4. Bump Spring + Spring Security + Hibernate; rewrite
   `WebSecurityConfigurerAdapter` subclasses to `SecurityFilterChain` beans.
5. Run OpenRewrite recipe.
6. Audit Hibernate 6 implicit naming-strategy schema deltas.
7. Remove the `jakarta-migrate-and-start.sh` entrypoint and the migration
   step from the Dockerfiles.

Once C.2 lands, the WAR is natively Jakarta-typed and the deploy-time
migration step retires.

---

## Related Decisions

- ADR 0001: Monorepo structure
- ADR 0002: Gradle multi-project build
- ADR 0003: Java 21 target
- ADR 0004: Single-EC2 deploy target
- ADR 0006: EKS deploy target (uses the same migrated WAR)
- ADR 0007: Circular dependency resolution (deferred to C.2)
