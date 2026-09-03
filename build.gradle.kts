plugins {
    `java-library`
    id("com.diffplug.spotless") version "8.10.0"
    id("net.fabricmc.fabric-loom") version "1.17.20" apply false
    `maven-publish`
}

group = "dev.melontrick.render3dfw"
version = providers.gradleProperty("mod_version").get()

base {
    archivesName = "render3d-core"
}

allprojects {
    group = rootProject.group
    version = rootProject.version
}

subprojects {
    pluginManager.withPlugin("java") {
        extensions.configure<JavaPluginExtension> {
            withSourcesJar()
        }

        tasks.withType<JavaCompile>().configureEach {
            options.release = 25
            options.encoding = "UTF-8"
            options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}

java {
    withSourcesJar()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

spotless {
    java {
        target("src/**/*.java", "fabric-*/src/**/*.java")
        palantirJavaFormat("2.97.0").formatJavadoc(false)
        removeUnusedImports()
        forbidWildcardImports()
        formatAnnotations()
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
