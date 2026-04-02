# XNAT Web Application Dockerfile
# Based on the official NrgXnat/xnat-docker-compose Dockerfile,
# adapted for Java 21 and runtime WAR mounting (WAR is not baked into image).

FROM tomcat:9.0-jdk21-temurin

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

# ----- helper scripts -----
COPY make-xnat-config.sh /usr/local/bin/make-xnat-config.sh
COPY wait-for-postgres.sh /usr/local/bin/wait-for-postgres.sh
RUN chmod +x /usr/local/bin/make-xnat-config.sh /usr/local/bin/wait-for-postgres.sh

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

# Wait for Postgres to be ready, then start Tomcat.
# The WAR file is expected to be mounted at runtime as ROOT.war.
CMD ["wait-for-postgres.sh", "/usr/local/tomcat/bin/catalina.sh", "run"]
