plugins {
    java
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.shardedmc"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.yaml:snakeyaml:2.2")
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.23.1")
    implementation("org.apache.logging.log4j:log4j-core:2.23.1")
    implementation("com.google.guava:guava:33.2.1-jre")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.shardedmc.ShardedMC")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "com.shardedmc.ShardedMC",
            "Implementation-Title" to "ShardedMC",
            "Implementation-Version" to project.version
        )
    }
}

tasks.shadowJar {
    archiveBaseName.set("ShardedMC")
    archiveClassifier.set("")
    archiveVersion.set("")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.register<JavaExec>("benchmark") {
    group = "verification"
    description = "Run ShardedMC benchmark suite"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.shardedmc.benchmark.BenchmarkRunner")
}
