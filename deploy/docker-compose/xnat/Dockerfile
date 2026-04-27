# XNAT Web Application Dockerfile
# Based on the official NrgXnat/xnat-docker-compose Dockerfile,
# adapted for Java 21 and runtime WAR mounting (WAR is not baked into image).
#
# Phase C.1: bumped from tomcat:9.0-jdk21-temurin to tomcat:10.1-jdk21-temurin.
# The JakartaTransformerListener (declared in apps/web/.../META-INF/context.xml)
# rewrites javax.* -> jakarta.* references at class load time, so the same
# javax-typed WAR runs on Tomcat 10's jakarta-only Servlet API. No source
# changes needed — that's deferred to Phase C.2.

FROM tomcat:10.1-jdk21-temurin

LABEL org.opencontainers.image.authors="XNAT Monorepo"

# ----- build-time defaults (overridable via docker-compose build args) -----
ARG XNAT_ROOT=/data/xnat
ARG XNAT_HOME=/data/xnat/home
ARG XNAT_DATASOURCE_DRIVER=org.postgresql.Driver
ARG XNAT_DATASOURCE_URL=jdbc:postgresql://xnat-db:5432/xnat
ARG XNAT_DATASOURCE_USERNAME=xnat
ARG XNAT_DATASOURCE_PASSWORD=xnat
ARG XNAT_EMAIL=admin@example.com
ARG XNAT_SMTP_ENABLED=false
ARG XNAT_SMTP_HOSTNAME=
ARG XNAT_SMTP_PORT=
ARG XNAT_SMTP_AUTH=
ARG XNAT_SMTP_USERNAME=
ARG XNAT_SMTP_PASSWORD=
ARG XNAT_SMTP_START_TLS=
ARG TOMCAT_XNAT_FOLDER=ROOT

# PostgreSQL JDBC driver version — must match what XNAT expects
ARG PG_JDBC_VERSION=42.7.4

# ----- install OS packages -----
RUN apt-get update && \
    apt-get install -y --no-install-recommends postgresql-client wget && \
    rm -rf /var/lib/apt/lists/*

# ----- remove default Tomcat webapps -----
RUN rm -rf ${CATALINA_HOME}/webapps/*

# ----- PostgreSQL JDBC driver -----
# Tomcat needs the driver in its shared lib so the DataSource can load it.
RUN wget --quiet --output-document=${CATALINA_HOME}/lib/postgresql-${PG_JDBC_VERSION}.jar \
    "https://jdbc.postgresql.org/download/postgresql-${PG_JDBC_VERSION}.jar"

# ----- create XNAT directory structure -----
RUN mkdir -p \
    ${XNAT_HOME}/config \
    ${XNAT_HOME}/logs \
    ${XNAT_HOME}/plugins \
    ${XNAT_HOME}/work \
    ${XNAT_ROOT}/archive \
    ${XNAT_ROOT}/build \
    ${XNAT_ROOT}/cache \
    ${XNAT_ROOT}/ftp \
    ${XNAT_ROOT}/pipeline \
    ${XNAT_ROOT}/prearchive \
    ${XNAT_ROOT}/inbox

# ----- Tomcat tuning for XNAT -----
# Skip scanning JARs with cyclic inheritance (BouncyCastle) to avoid StackOverflowError,
# and skip other large JARs that don't contain annotations to speed up startup.
RUN sed -i 's/^tomcat.util.scan.StandardJarScanFilter.jarsToSkip=\\/&\nbcpkix-*.jar,bcprov-*.jar,bcutil-*.jar,\\/' \
    ${CATALINA_HOME}/conf/catalina.properties

# Increase default stack size for annotation scanning
ENV JDK_JAVA_OPTIONS="--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.util.concurrent=ALL-UNNAMED --add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED"

# ----- helper scripts -----
COPY make-xnat-config.sh /usr/local/bin/make-xnat-config.sh
COPY wait-for-postgres.sh /usr/local/bin/wait-for-postgres.sh
COPY jakarta-migrate-and-start.sh /usr/local/bin/jakarta-migrate-and-start.sh
RUN chmod +x /usr/local/bin/make-xnat-config.sh \
             /usr/local/bin/wait-for-postgres.sh \
             /usr/local/bin/jakarta-migrate-and-start.sh

# ----- generate default xnat-conf.properties at build time -----
# The config is written to a staging area; at container start the entrypoint
# copies it to XNAT_HOME/config only if no config file is already mounted.
RUN /usr/local/bin/make-xnat-config.sh

# ----- environment variables available at runtime -----
ENV XNAT_HOME=${XNAT_HOME} \
    XNAT_ROOT=${XNAT_ROOT} \
    XNAT_DATASOURCE_USERNAME=${XNAT_DATASOURCE_USERNAME} \
    PGPASSWORD=${XNAT_DATASOURCE_PASSWORD}

EXPOSE 8080

# Phase C.1 entrypoint: rewrite javax.* -> jakarta.* via the bundled
# jakartaee-migration tool, then chain into wait-for-postgres + Tomcat.
# The mounted WAR is read from /opt/xnat-input/ROOT.war and the
# transformed copy is placed at /usr/local/tomcat/webapps/ROOT.war.
CMD ["jakarta-migrate-and-start.sh"]
