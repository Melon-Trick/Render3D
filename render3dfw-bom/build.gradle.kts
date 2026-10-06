plugins {
    `java-platform`
    `maven-publish`
}

dependencies {
    constraints {
        rootProject.subprojects.filter { it != project }.forEach { api(project(it.path)) }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenPlatform") { from(components["javaPlatform"]) }
    }
}
