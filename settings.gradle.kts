pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "Render3DFW"

include("render3dfw-api")
include("render3dfw-engine")
include("render3dfw-bom")
