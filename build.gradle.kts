plugins {
    java
    kotlin("jvm") version "2.4.20"
}

group = "net.gasmp"
version = providers.gradleProperty("releaseVersion").getOrElse("0.8.3")

// Paper 26.2 is the documented ItemsAdder 4.0.18 runtime target.
// Paper 26.3 is compile/test only until ItemsAdder lists it as supported.
val paperApiVersion = providers.gradleProperty("paperApiVersion").orElse("26.2.build.+")
val paperTarget = providers.gradleProperty("paperTarget").orElse("")

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${paperApiVersion.get()}")
    compileOnly("net.luckperms:api:5.5")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    // Optional presentation API. ItemsAdder's premium JAR is not bundled:
    // GASMP accesses ItemsAdder 4's public API via reflection at runtime.
    compileOnly("com.github.VOIDRP-MINECRAFT:voidrp-ui:v0.4.0")
    compileOnly(kotlin("stdlib"))

    implementation("org.xerial:sqlite-jdbc:3.54.1.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

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
