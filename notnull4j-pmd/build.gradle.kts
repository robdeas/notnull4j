plugins {
    `java-library`
    `maven-publish`
    pmd
}

group = "tech.robd"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // PMD framework for rule development

    // Project dependency (ensure this project exists in your settings.gradle.kts)
    compileOnly(project(":notnull4j-core"))

    // 1. PMD 7 Engine
    compileOnly("net.sourceforge.pmd:pmd-java:7.21.0")

    // 2. Project core
    compileOnly(project(":notnull4j-core"))

    // 3. Testing
    testImplementation("net.sourceforge.pmd:pmd-test:7.21.0")


    // Standard JUnit 5 implementation
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

pmd {
    toolVersion = "7.21.0"
    // Setting ruleSets to empty ensures ONLY your custom rules run
    ruleSets = listOf()
    ruleSetFiles = files("src/main/resources/notnull4j-ruleset.xml")
}



java {
    toolchain {
        // Build with the modern JDK
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

//tasks.withType<JavaCompile>().configureEach {
//    // Strictly enforce Java 11 bytecode and API usage
//    options.release.set(11)
//}


publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("NotNull4J PMD Rules")
                description.set("PMD rules for enforcing @LocalNotNull usage with NotNull4J")
                url.set("https://github.com/robdeas/notnull4j")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("robdeas")
                        name.set("Rob Deas")
                        email.set("rob@robd.tech")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/robdeas/notnull4j.git")
                    developerConnection.set("scm:git:ssh://github.com/robdeas/notnull4j.git")
                    url.set("https://github.com/robdeas/notnull4j")
                }
            }
        }
    }
}