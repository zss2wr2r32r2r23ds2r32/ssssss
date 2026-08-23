dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
}

tasks.jar {
    archiveFileName.set("ShardedVelocityCore-Backend-${project.version}.jar")
}

tasks.register<Copy>("copyArtifacts") {
    dependsOn(tasks.jar)
    from(tasks.jar)
    into("/opt/cursor/artifacts")
}

tasks.build {
    finalizedBy("copyArtifacts")
}
