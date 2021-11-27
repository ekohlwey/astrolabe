import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    application
    kotlin("jvm") version "1.6.0"
    id("com.palantir.graal") version "0.9.0"
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

group = "com.github.ekohlwey.astrolabe"
version = "1.0.0"

repositories {
    mavenCentral()
}

application {
    mainClass.set("com.github.ekohlwey.astrolabe.Astrolabe")
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions.freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("info.picocli:picocli:4.6.2")
    implementation("com.fazecast:jSerialComm:2.7.0")
    implementation("org.slf4j:slf4j-simple:1.7.32")
    //implementation("com.diogonunes:JColor:5.2.0")
    implementation("com.github.h0tk3y.betterParse:better-parse:0.4.3")
    implementation("io.github.microutils:kotlin-logging-jvm:2.0.11")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0-RC")
//    implementation("org.fusesource.jansi:jansi:2.4.0")
    implementation("org.jline:jline:3.21.0")
    implementation("org.jline:jline-terminal-jansi:3.21.0")
    compileOnly("org.graalvm.nativeimage:svm:21.2.0")
    annotationProcessor("info.picocli:picocli-codegen:4.6.2")
    annotationProcessor("org.graalvm.nativeimage:svm:21.2.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-runner-junit5:5.0.0.RC2")
    testImplementation("io.kotest:kotest-assertions-core:5.0.0.RC2")
    testImplementation("io.kotest:kotest-property:5.0.0.RC2")
}

tasks.test {
    useJUnitPlatform()
}

//kotlin {
//    sourceSets.all {
//        languageSettings.apply {
//            languageVersion = "1.6"
//            //progressiveMode = true
//        }
//    }
//}

java {
    version = "11"
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
