plugins {
    `java-library` apply true
    `maven-publish` apply true
}


allprojects {
    group = "tech.robd"
    version = "1.0.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    tasks.withType<Test> {
        useJUnitPlatform()

        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
        }
    }
}