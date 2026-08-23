dependencies {
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
}

tasks.jar {
    archiveFileName.set("ShardedVelocityCore-Lobby-${project.version}.jar")
}

tasks.register<Copy>("copyArtifacts") {
    dependsOn(tasks.jar)
    from(tasks.jar)
    into("/opt/cursor/artifacts")
}

tasks.build {
    finalizedBy("copyArtifacts")
}
