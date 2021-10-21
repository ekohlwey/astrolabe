plugins {
    java
    application
    kotlin("jvm") version "1.5.31"
    id("com.palantir.graal") version "0.9.0"
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

group="com.github.ekohlwey.astrolabe"
version="1.0.0"

java {
    version = "11"
}

repositories {
    mavenCentral()
    jcenter()
}

application {
    mainClass.set("com.github.ekohlwey.astrolabe.Astrolabe")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("info.picocli:picocli:4.6.1")
    implementation("com.fazecast:jSerialComm:2.7.0")
    implementation("org.slf4j:slf4j-simple:1.7.2")
//    implementation("ch.qos.logback:logback-classic:1.2.6")
    compileOnly("org.graalvm.nativeimage:svm:20.3.4")
    annotationProcessor("info.picocli:picocli-codegen:4.6.1")
    annotationProcessor("org.graalvm.nativeimage:svm:20.3.4")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

graal {
    mainClass("com.github.ekohlwey.astrolabe.Astrolabe")
    outputName("a7e")
    javaVersion("11")
    graalVersion("20.3.4")
    option("--no-fallback")
    option("--initialize-at-build-time=sun.reflect.Reflection")
    // try to add "--no-fallback"
}

tasks {
    test {
        useJUnitPlatform()
    }
    jar {
        manifest {
            attributes(
                Pair("Main-Class", "com.github.ekohlwey.astrolabe.Astrolabe")
            )
        }
    }
}
