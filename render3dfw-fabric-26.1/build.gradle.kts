plugins {
    `java-library`
    id("net.fabricmc.fabric-loom")
}

base {
    archivesName = "render3dfw-fabric-26.1"
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")

    api(project(":render3dfw-engine"))
    include(project(":render3dfw-api"))
    include(project(":render3dfw-engine"))
}

tasks.processResources {
    val properties = mapOf("version" to project.version)
    inputs.properties(properties)
    filesMatching("fabric.mod.json") {
        expand(properties)
    }
}
