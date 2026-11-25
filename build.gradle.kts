plugins {
    java
    kotlin("jvm") version "2.4.20"
}

group = "net.gasmp"
version = providers.gradleProperty("releaseVersion").getOrElse("0.8.3")

// Build the same plugin version against separate Paper API lines.
val paperApiVersion = providers.gradleProperty("paperApiVersion").orElse("26.2.build.+")
val paperTarget = providers.gradleProperty("paperTarget").orElse("")

repositories {
    // Resolve Paper API coordinates directly from Paper first; Maven Central remains
    // available for transitive and third-party dependencies.
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://repo.momirealms.net/releases/")
    maven("https://api.modrinth.com/maven")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${paperApiVersion.get()}")
    // CraftEngine 26.9.2 plugin jar exposes the public Bukkit/Core API to compile against.
    compileOnly("maven.modrinth:tRX6FMfQ:mwFiNxVO")
    compileOnly("com.github.VOIDRP-MINECRAFT:voidrp-ui:v0.4.0")
    compileOnly(kotlin("stdlib"))
    compileOnly("net.luckperms:api:5.5")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    implementation("org.xerial:sqlite-jdbc:3.54.1.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

// VoidRP UI API is Kotlin/JVM 21 bytecode, built with JDK 25 against Paper 26.2.
kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

configurations.compileClasspath {
    attributes {
        attribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}
configurations.testCompileClasspath {
    attributes {
        attribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.jar {
    archiveBaseName.set("GASMP-Core")
    archiveClassifier.set(paperTarget.get())
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { dep ->
        if (dep.isDirectory) dep else zipTree(dep)
    }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/**/module-info.class")
    }
}
