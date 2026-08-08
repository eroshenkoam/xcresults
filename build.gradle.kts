plugins {
    java
    id("org.graalvm.buildtools.native") version "0.9.24"
}

group = "org.example"

description = "Allure Server Java Client"

tasks.withType(Wrapper::class) {
    gradleVersion = "7.5.1"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

tasks.withType(JavaCompile::class) {
    options.encoding = "UTF-8"
}

tasks.processResources {
    inputs.property("version", project.version.toString())
    filesMatching("version.txt") {
        expand("version" to project.version)
    }
}

graalvmNative {
    toolchainDetection.set(true)
    binaries {
        named("main") {
            mainClass.set("io.eroshenkoam.xcresults.XCResults")
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    annotationProcessor("info.picocli:picocli-codegen:4.1.4")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.10.2")
    implementation("io.qameta.allure:allure-model:2.13.1")
    implementation("org.freemarker:freemarker:2.3.32")
    implementation("info.picocli:picocli:4.1.4")
    implementation("commons-io:commons-io:2.6")

    testImplementation("junit:junit:4.12")
}
