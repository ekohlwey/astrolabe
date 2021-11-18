import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    application
    kotlin("jvm") version "1.6.0-RC2"
    id("com.palantir.graal") version "0.9.0"
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

group="com.github.ekohlwey.astrolabe"
version="1.0.0"

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
    implementation("info.picocli:picocli:4.6.1")
    implementation("com.fazecast:jSerialComm:2.7.0")
    implementation("org.slf4j:slf4j-simple:1.7.2")
    //implementation("com.diogonunes:JColor:5.2.0")
    implementation("com.github.h0tk3y.betterParse:better-parse:0.4.2")
    implementation ("io.github.microutils:kotlin-logging-jvm:2.0.10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
    implementation("org.fusesource.jansi:jansi:2.4.0")
    compileOnly("org.graalvm.nativeimage:svm:20.3.4")
    annotationProcessor("info.picocli:picocli-codegen:4.6.1")
    annotationProcessor("org.graalvm.nativeimage:svm:20.3.4")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
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
