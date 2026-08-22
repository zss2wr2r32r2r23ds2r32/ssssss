dependencies {
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
    implementation(project(":common"))
}

tasks.jar {
    archiveBaseName.set("ShardedVelocityCore-PlaceholderAPI")
    archiveClassifier.set("")
}
