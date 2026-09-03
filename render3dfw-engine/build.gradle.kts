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
