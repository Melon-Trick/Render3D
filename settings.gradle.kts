pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net")
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://maven.fabricmc.net")
        mavenCentral()
    }
}

rootProject.name = "Render3DFW"

include("render3dfw-api")
include("render3dfw-engine")
include("render3dfw-fabric-26.1")
