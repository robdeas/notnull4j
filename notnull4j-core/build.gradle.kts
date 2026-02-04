// SPDX-License-Identifier: MIT

/*
 * Copyright (c) 2026 Rob Deas (tech.robd)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
plugins {
    `java-library`
    `maven-publish`
}

group = "tech.robd"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    // API dependencies - users need these at compile-time
    api("org.jspecify:jspecify:1.0.0")

    // Implementation dependencies - internal only
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("ch.qos.logback:logback-core:1.5.27")
    implementation("ch.qos.logback:logback-classic:1.5.27")
    // Test dependencies
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("ch.qos.logback:logback-classic:1.5.27")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
    testImplementation("org.mockito:mockito-core:5.21.0")
}



java {
    toolchain {
        // Use 17 to match the rest of the project
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    // need Java 11 output
    options.release.set(11)
}


tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("NotNull4J Core")
                description.set("Runtime null-safety utilities for Java with JSpecify integration")
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