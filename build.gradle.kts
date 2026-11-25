plugins { java }

group = "net.gasmp"
version = providers.gradleProperty("releaseVersion").getOrElse("0.8.3")\n\n// Build the same plugin version against separate Paper API lines.\nval paperApiVersion = providers.gradleProperty("paperApiVersion").orElse("26.2.build.+")\nval paperTarget = providers.gradleProperty("paperTarget").orElse("paper-26.2")

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${paperApiVersion.get()}")
    compileOnly("net.luckperms:api:5.5")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    implementation("org.xerial:sqlite-jdbc:3.54.1.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.jar {
    archiveBaseName.set("GASMP-Core")\n    archiveClassifier.set(paperTarget.get())
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { dep ->
        if (dep.isDirectory) dep else zipTree(dep)
    }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/**/module-info.class")
    }
}
