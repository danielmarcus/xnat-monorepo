plugins {
    id("xnat-java-library")
}

group = "org.nrg.xdat"
version = libs.versions.xnat.get()

configurations.configureEach {
    exclude(group = "javax.sql", module = "jdbc-stdext")
    exclude(group = "javax.transaction", module = "jta")
}

dependencies {
    // --- Internal modules ---
    api(project(":libs:notify"))
    api(project(":libs:config"))
    api(project(":libs:mail"))
    api(project(":libs:framework"))
    api(project(":libs:automation"))

    // --- Spring ---
    implementation(libs.spring.core)
    implementation(libs.spring.context)
    implementation(libs.spring.context.support)
    implementation(libs.spring.web)
    implementation(libs.spring.jdbc)
    implementation(libs.spring.security.core)
    implementation(libs.spring.security.web)
    implementation(libs.spring.jms)

    // --- Messaging ---
    implementation(libs.activemq.client)

    // --- AOP ---
    implementation(libs.aspectjweaver)
    implementation(libs.aspectjrt)

    // --- Reactor ---
    implementation(libs.io.projectreactor.core)
    implementation("io.projectreactor:reactor-bus:2.0.8.RELEASE")

    // --- Hibernate ---
    implementation(libs.hibernate.core)

    // --- Jackson ---
    implementation(libs.jackson.databind)

    // --- Logging ---
    implementation(libs.slf4j.api)
    implementation(libs.jul.to.slf4j)
    implementation(libs.log4j.over.slf4j)

    // --- Servlet / JMS ---
    compileOnly(libs.javax.servlet.api)
    implementation("javax.jms:javax.jms-api:2.0.1")
    implementation("javax.management.j2ee:javax.management.j2ee-api:1.1.1")

    // --- Jakarta Inject ---
    implementation("jakarta.inject:jakarta.inject-api:1.0.3")

    // --- Utilities ---
    implementation(libs.commons.lang3)
    implementation(libs.commons.io)
    implementation(libs.commons.codec)
    implementation(libs.commons.fileupload)
    implementation(libs.commons.configuration)
    implementation(libs.guava)
    implementation(libs.reflections)
    implementation(libs.dom4j)
    implementation(libs.restlet)
    implementation("org.apache.commons:commons-text:1.10.0")
    implementation("org.apache.commons:commons-csv:1.10.0")
    implementation("commons-net:commons-net:3.9.0")
    implementation("org.apache.httpcomponents:httpclient:4.5.14")
    implementation("org.apache.commons:commons-dbcp2:2.9.0")

    // --- Legacy deps (from XNAT Artifactory) ---
    implementation("turbine:turbine:2.3.3") {
        exclude(group = "velocity", module = "texen")
        exclude(group = "fulcrum")
        exclude(group = "jamon")
        exclude(group = "commons-logging")
        exclude(group = "log4j")
        exclude(group = "servlet")
    }
    implementation("torque:torque:3.0") {
        exclude(group = "commons-logging")
        exclude(group = "log4j")
        exclude(group = "servlet")
        exclude(group = "fulcrum")
        exclude(group = "jamon")
        exclude(group = "velocity", module = "texen")
        exclude(group = "javax.sql", module = "jdbc-stdext")
        exclude(group = "javax.transaction", module = "jta")
    }
    implementation("org.apache.velocity:velocity:1.7") {
        exclude(group = "commons-logging")
    }
    implementation("org.apache.velocity:velocity-tools:2.0") {
        exclude(group = "commons-logging")
        exclude(group = "commons-digester")
    }
    implementation("com.noelios.restlet:com.noelios.restlet.ext.servlet:1.1.10")
    implementation("ecs:ecs:1.4.2")
    implementation("com.lowagie:itext:2.1.7")
    implementation("fop:fop:0.20.5")
    implementation("org.json:json:20231013")
    implementation("eu.bitwalker:UserAgentUtils:1.21")

    // --- Scripting ---
    implementation(libs.jython.standalone)

    // --- Cache ---
    implementation(libs.ehcache3)
    implementation(libs.ant)

    // --- Scheduling ---
    implementation(libs.quartz)

    // --- Database ---
    implementation(libs.postgresql)

    // --- Compile-only ---
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    // --- Test ---
    // libs:test exposes org.nrg.test.utils.{TestBeans,TestFileUtils} that
    // TestXdatUserAuthServiceConfig imports.
    testImplementation(project(":libs:test"))
    testImplementation(libs.junit4)
    testImplementation(libs.mockito.core)
    testImplementation(libs.spring.test)
    testImplementation(libs.assertj.core)
    testImplementation(libs.h2)
    testImplementation(libs.logback.classic)
    testImplementation(libs.hibernate.jcache)
}
