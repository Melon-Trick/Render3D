plugins {
    `java-library`
    `maven-publish`
}

base {
    archivesName = "render3dfw-engine"
}

dependencies {
    api(project(":render3dfw-api"))
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}

val profileMode = providers.gradleProperty("profileMode").orElse("uncached")
val profileDirectory = layout.buildDirectory.dir("profiles")
val testSourceSet = sourceSets.named("test")
val selectedProfileMode = profileMode.get()
val selectedProfileDirectory = profileDirectory.get()

val profileMassiveFrame = tasks.register<JavaExec>("profileMassiveFrame") {
    dependsOn(tasks.named("testClasses"))
    classpath = testSourceSet.get().runtimeClasspath
    mainClass = "dev.melontrick.render3dfw.profile.MassiveFrameProfile"
    maxHeapSize = "3g"
    args(selectedProfileMode, selectedProfileDirectory.file("massive-$selectedProfileMode.jfr").asFile.absolutePath)
}

tasks.register<JavaExec>("flameGraphMassiveFrame") {
    dependsOn(profileMassiveFrame)
    classpath = testSourceSet.get().runtimeClasspath
    mainClass = "dev.melontrick.render3dfw.profile.JfrFlameGraph"
    args(
        selectedProfileDirectory.file("massive-$selectedProfileMode.jfr").asFile.absolutePath,
        selectedProfileDirectory.file("massive-$selectedProfileMode-flamegraph.svg").asFile.absolutePath,
        "Render3DFW 500k - $selectedProfileMode",
    )
}
