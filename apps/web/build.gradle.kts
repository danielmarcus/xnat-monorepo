plugins {
    id("xnat-war-application")
}

group = "org.nrg.xnat"
version = libs.versions.xnat.get()
description = "XNAT is an open-source imaging informatics software platform dedicated to helping you perform imaging-based research."

configurations.configureEach {
    exclude(group = "ant", module = "ant")
    exclude(group = "avalon-logkit")
    exclude(group = "berkeleydb")
    exclude(group = "com.github.jai-imageio")
    exclude(group = "com.google.code.findbugs", module = "annotations")
    exclude(group = "com.metaparadigm")
    exclude(group = "com.sun.jdmk", module = "jmxtools")
    exclude(group = "com.sun.jmx", module = "jmxri")
    exclude(group = "commons-beanutils", module = "commons-beanutils-core")
    exclude(group = "commons-betwixt")
    exclude(group = "commons-dbcp")
    exclude(group = "commons-email")
    exclude(group = "commons-logging")
    exclude(group = "commons-messenger")
    exclude(group = "commons-pool")
    exclude(group = "edu.ucar", module = "netcdf")
    exclude(group = "excalibur-component")
    exclude(group = "excalibur-instrument")
    exclude(group = "excalibur-logger")
    exclude(group = "excalibur-pool")
    exclude(group = "fulcrum")
    exclude(group = "geronimo-spec")
    exclude(group = "hsqldb")
    exclude(group = "imagej")
    exclude(group = "jakarta-regexp")
    exclude(group = "jamon")
    exclude(group = "javax.jms", module = "jms")
    exclude(group = "javax.mail", module = "mail")
    exclude(group = "javax.servlet", module = "servlet-api")
    exclude(group = "javax.sql", module = "jdbc-stdext")
    exclude(group = "javax.transaction", module = "jta")
    exclude(group = "javax.xml", module = "jsr173")
    exclude(group = "jdbc", module = "jdbc")
    exclude(group = "jmock")
    exclude(group = "jms", module = "jms")
    exclude(group = "jndi")
    exclude(group = "jython")
    exclude(group = "log4j", module = "log4j")
    exclude(group = "mockobjects")
    exclude(group = "mysql")
    exclude(group = "net.sf.saxon")
    exclude(group = "ojb", module = "ojb")
    exclude(group = "org.apache.geronimo.specs")
    exclude(group = "org.apache.struts")
    exclude(group = "org.nrg", module = "nrg")
    exclude(group = "org.nrg", module = "nrgutil")
    exclude(group = "org.nrg", module = "plexiviewer")
    exclude(group = "org.nrg.xdat", module = "beans")
    exclude(group = "org.slf4j", module = "slf4j-log4j12")
    exclude(group = "quartz")
    exclude(group = "resources", module = "resources")
    exclude(group = "servletapi")
    exclude(group = "stax", module = "stax-api")
    exclude(group = "tomcat")
    exclude(group = "velocity")
    exclude(group = "xalan")
    exclude(group = "xerces")
    exclude(group = "xml-apis")
    exclude(group = "xml-resolver")
    exclude(group = "xmlrpc")
    exclude(module = "log4j-slf4j-impl")
    exclude(module = "pipelineCNDAXNAT")
    exclude(module = "slf4j-simple")
}

dependencies {
    // --- Annotation processors ---
    annotationProcessor(project(":libs:framework"))
    annotationProcessor(libs.auto.value)

    // --- Internal modules (api) ---
    api(project(":libs:xdat"))
    api(project(":build-tools:xnat-data-models"))
    api(project(":libs:framework"))

    // --- Internal modules (implementation) ---
    implementation(project(":libs:spawner"))
    implementation(project(":libs:transaction"))
    implementation(project(":libs:prefs"))
    implementation(project(":libs:config"))
    implementation(project(":libs:automation"))
    implementation(project(":libs:dicomtools"))
    implementation(project(":libs:dicom-edit4"))
    implementation(project(":libs:dicom-edit6"))
    implementation(project(":libs:mail"))
    implementation(project(":libs:notify"))
    implementation(project(":libs:dicom-xnat:dicom-xnat-mx"))
    implementation(project(":libs:dicom-xnat:dicom-xnat-sop"))
    implementation(project(":libs:dicom-xnat:dicom-xnat-util"))
    implementation(project(":libs:ecat4xnat"))
    implementation(project(":libs:extattr"))
    implementation(project(":libs:dicom-image-utils"))
    implementation(project(":libs:prearc-importer"))
    implementation(project(":libs:session-builders"))

    // --- External: mizer (from XNAT Artifactory) ---
    implementation("org.nrg.dicom:mizer:${project.version}")

    // --- Legacy dependencies ---
    implementation("turbine:turbine:2.3.3") {
        exclude(group = "velocity", module = "texen")
        exclude(group = "fulcrum")
        exclude(group = "jamon")
        exclude(group = "commons-logging")
        exclude(group = "log4j")
        exclude(group = "servlet")
        exclude(group = "javax.sql", module = "jdbc-stdext")
        exclude(group = "javax.transaction", module = "jta")
    }
    implementation("org.apache.velocity:velocity:1.7")
    implementation("org.apache.velocity:velocity-tools:2.0")
    implementation("javax.jms:javax.jms-api:2.0.1")

    // --- Spring Framework ---
    api(libs.spring.security.ldap)
    api(libs.spring.security.oauth2)
    api(libs.spring.security.jwt)
    api(libs.spring.web)
    api(libs.spring.jdbc)
    api(libs.spring.beans)
    api(libs.spring.context)
    api(libs.spring.core)
    implementation(libs.spring.aop)
    implementation(libs.spring.context.support)
    implementation(libs.spring.jms)
    implementation(libs.spring.messaging)
    implementation(libs.spring.orm)
    implementation(libs.spring.oxm)
    implementation(libs.spring.tx)
    implementation(libs.spring.webmvc)

    // --- Spring Security ---
    implementation(libs.spring.security.acl)
    implementation(libs.spring.security.aspects)
    implementation(libs.spring.security.config)
    implementation(libs.spring.security.taglibs)
    implementation(libs.spring.ldap.core)

    // --- Swagger ---
    implementation(libs.swagger.springmvc)
    implementation(libs.swagger.ui)

    // --- Hibernate / Cache ---
    implementation(libs.hibernate.core)
    implementation(libs.hibernate.jcache)
    implementation(libs.hibernate.validator)
    implementation(libs.hibernate.envers)
    implementation(libs.ehcache3)
    implementation(libs.redisson)
    implementation("com.vladmihalcea:hibernate-types-55:2.14.0")

    // --- AOP ---
    implementation(libs.aspectjweaver)
    implementation(libs.aspectjrt)

    // --- Restlet ---
    implementation(libs.restlet)
    implementation("com.noelios.restlet:com.noelios.restlet:1.1.10")
    implementation("com.noelios.restlet:com.noelios.restlet.ext.servlet:1.1.10")
    implementation("org.restlet:org.restlet.ext.fileupload:1.1.10")

    // --- Jackson ---
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.datatype.guava)
    implementation(libs.jackson.module.parameter.names)
    implementation(libs.jackson.datatype.jdk8)
    implementation(libs.jackson.datatype.jsr310)
    implementation("com.jayway.jsonpath:json-path:2.9.0")
    implementation("org.json:json:20231013")

    // --- Apache Commons ---
    implementation(libs.commons.beanutils)
    implementation(libs.commons.codec)
    implementation(libs.commons.collections)
    implementation(libs.commons.configuration)
    implementation("commons-digester:commons-digester:2.1")
    implementation("commons-discovery:commons-discovery:0.5")
    implementation(libs.commons.fileupload)
    implementation("commons-net:commons-net:3.8.0")
    implementation(libs.commons.configuration2)
    implementation("org.apache.commons:commons-math:2.2")
    implementation("org.apache.commons:commons-text:1.11.0")
    implementation("org.apache.httpcomponents:httpclient:4.5.14")
    implementation("org.apache.httpcomponents:httpcore:4.4.16")
    implementation("org.apache.httpcomponents:httpcore-nio:4.4.16")
    implementation("org.apache.httpcomponents:httpmime:4.5.14")
    implementation(libs.commons.pool2)
    implementation(libs.commons.compress)

    // --- Scripting ---
    implementation(libs.groovy.all)

    // --- DICOM ---
    implementation(libs.dcm4che2.core)
    implementation(libs.dcm4che2.image)
    implementation(libs.dcm4che2.imageio)
    implementation(libs.dcm4che2.imageio.rle)
    implementation(libs.dcm4che2.iod)
    implementation(libs.dcm4che2.net)
    implementation("net.imagej:ij:1.54f")
    implementation(libs.dcm4che5.core)
    implementation(libs.dcm4che5.net)
    implementation(libs.dcm4che5.image)
    implementation(libs.dcm4che5.imageio)

    // --- Miscellaneous ---
    implementation("eu.bitwalker:UserAgentUtils:1.21")
    implementation("com.twmacinta:fast-md5:2.7.1")
    implementation(libs.h2)
    implementation(libs.msv.core)
    implementation("gnu.getopt:java-getopt:1.0.13")
    implementation(libs.quartz)
    implementation(libs.reflections)
    implementation("org.apache.xbean:xbean-spring:4.20")
    implementation("net.java.dev.msv:xsdlib:2013.6.1")
    implementation("javax.servlet:jstl:1.2")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation(libs.guava)
    implementation(libs.postgresql)
    implementation(libs.datasource.proxy)

    // --- ActiveMQ ---
    implementation(libs.activemq.broker)
    implementation(libs.activemq.spring)
    implementation(libs.activemq.kahadb.store)
    implementation(libs.activemq.jdbc.store)

    // --- Logging ---
    implementation(libs.slf4j.api)
    implementation(libs.jul.to.slf4j)
    implementation(libs.jcl.over.slf4j)
    implementation(libs.log4j.over.slf4j)
    implementation(libs.logback.classic)
    implementation(libs.logback.core)
    implementation(libs.logstash.encoder)
    implementation(libs.auto.value.annotations)

    // --- JDK 21: javax.mail, javax.activation ---
    implementation("com.sun.mail:javax.mail:1.6.2")
    implementation("javax.mail:javax.mail-api:1.6.2")
    implementation("javax.activation:activation:1.1.1")
    implementation("javax.inject:javax.inject:1")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    implementation("javax.xml.bind:jaxb-api:2.3.1")
    runtimeOnly("org.glassfish.jaxb:jaxb-runtime:2.3.3")

    // --- Provided (compile-only) ---
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly(libs.javax.servlet.api)
    compileOnly("org.kohsuke.metainf-services:metainf-services:1.11")

    // --- Runtime-only ---
    runtimeOnly(libs.ant)
    runtimeOnly(libs.cglib)
    runtimeOnly(libs.hikaricp)
    runtimeOnly(libs.hsqldb)
    runtimeOnly("com.sun.media:jai_imageio:1.2-pre-dr-b04")
    runtimeOnly("net.bull.javamelody:javamelody-core:1.91.0")
    runtimeOnly(libs.javassist)
    runtimeOnly(libs.jython.standalone)
    runtimeOnly(libs.bouncycastle.bcpkix)

    // --- Test ---
    testImplementation(project(":libs:test"))
    testImplementation(libs.junit4)
    testImplementation(libs.spring.test)
    testImplementation(libs.assertj.core)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.spring.security.config)
    testImplementation("org.hamcrest:hamcrest:2.2")
    testImplementation(libs.mockito.core)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
}

tasks.withType<Jar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.WARN
}
