plugins {
    id("xnat-java-library")
}

group = "org.nrg"
version = libs.versions.xnat.get()

dependencies {
    // --- Internal modules ---
    api(project(":libs:transaction"))
    api(project(":libs:extattr"))

    // --- Spring Framework ---
    api(libs.spring.core)
    api(libs.spring.beans)
    api(libs.spring.context)
    api(libs.spring.context.support)
    api(libs.spring.web)
    api(libs.spring.webmvc)
    api(libs.spring.jdbc)
    api(libs.spring.orm)
    api(libs.spring.tx)
    api(libs.spring.aop)
    api(libs.spring.oxm)
    api(libs.spring.jms)

    // --- Spring Security ---
    api(libs.spring.security.core)
    api(libs.spring.security.config)
    api(libs.spring.security.web)

    // --- Reactor ---
    api(libs.io.projectreactor.core)
    api("io.projectreactor:reactor-bus:2.0.8.RELEASE")

    // --- Hibernate ---
    api(libs.hibernate.core)
    api(libs.hibernate.validator)
    api(libs.hibernate.envers)
    api(libs.hibernate.jcache)
    api("javax.cache:cache-api:1.1.1")
    api("org.glassfish:javax.el:3.0.0")

    // --- Vladmihalcea Hibernate Types ---
    api("com.vladmihalcea:hibernate-types-55:2.14.0")

    // --- Jackson ---
    api(libs.jackson.core)
    api(libs.jackson.databind)
    api(libs.jackson.annotations)
    api(libs.jackson.dataformat.yaml)
    api(libs.jackson.datatype.guava)
    api(libs.jackson.datatype.hibernate5)

    // --- Logging ---
    api(libs.slf4j.api)

    // --- HTTP ---
    api("org.apache.httpcomponents:httpclient:4.5.14")

    // --- Apache Commons ---
    api(libs.commons.lang3)
    api(libs.commons.io)
    api(libs.commons.beanutils)
    api(libs.commons.configuration2)
    api("org.apache.commons:commons-text:1.10.0")

    // --- Utilities ---
    api(libs.guava)
    api(libs.reflections)
    api(libs.snakeyaml)
    api(libs.dom4j)
    api(libs.ant)

    // --- Cache ---
    api(libs.ehcache3)
    api(libs.redisson)

    // --- Scheduling ---
    api(libs.quartz)

    // --- Database ---
    api(libs.postgresql)
    api(libs.hikaricp)

    // --- AOP ---
    api(libs.aspectjweaver)

    // --- Servlet ---
    compileOnly(libs.javax.servlet.api)

    // --- JDK 21: javax.annotation for @PostConstruct ---
    api("javax.annotation:javax.annotation-api:1.3.2")

    // --- JDK 21: JAXB (removed from JDK 11+) ---
    api("javax.xml.bind:jaxb-api:2.3.1")
    api("org.glassfish.jaxb:jaxb-runtime:2.3.3")

    // --- JDK 21: javax.inject ---
    api("javax.inject:javax.inject:1")

    // --- Annotation processors ---
    api(libs.auto.value.annotations)
    annotationProcessor(libs.auto.value)

    // --- Compile-only ---
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("org.kohsuke.metainf-services:metainf-services:1.11")
    annotationProcessor("org.kohsuke.metainf-services:metainf-services:1.11")

    // --- Test annotation processors ---
    // SimpleBean (under src/test/java) is annotated with @XnatMixIn. The
    // XnatMixInAnnotationProcessor that turns it into
    // META-INF/xnat/serializers/*-mixin.properties files lives in this
    // module's own main code, so we need that classpath on the test
    // annotation-processor path to make it discoverable when
    // compileTestJava runs. Without this, testAnnotatedMixIn fails: no
    // mixin properties file is generated, SerializerService can't
    // register SimpleBeanMixIn, the @JsonIgnore never fires, and
    // `ignoredField` shows up in the JSON map.
    //
    // Use runtimeClasspath rather than just main.output because javac
    // loads EVERY processor declared in
    // META-INF/services/javax.annotation.processing.Processor, not only
    // the ones whose @SupportedAnnotationTypes match the source being
    // compiled. The other processors in this package
    // (XnatPluginAnnotationProcessor, NrgAbstractAnnotationProcessor)
    // reference SLF4J and commons-lang3; the kohsuke metainf-services
    // jar has to be there too. runtimeClasspath captures all of those
    // in one shot and stays correct as the processors evolve.
    testAnnotationProcessor(sourceSets.main.get().runtimeClasspath)
    testAnnotationProcessor("org.kohsuke.metainf-services:metainf-services:1.11")

    // --- Provided ---
    compileOnly(libs.h2)

    // --- Test ---
    // h2 is compileOnly above (provided by the host) but the framework's own
    // tests reference org.h2.api.ErrorCode in TestDBUtils, so it must be on
    // the test classpath too.
    testImplementation(libs.h2)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.spring.test)
    testImplementation(libs.logback.classic)
    testImplementation(libs.assertj.core)
    testImplementation(libs.cglib)
    testImplementation(libs.javassist)
    testImplementation("org.hamcrest:hamcrest-library:2.2")
    testImplementation("com.github.sbrannen:spring-test-junit5:1.5.0")
}
