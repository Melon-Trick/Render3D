import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    base
    id("com.diffplug.spotless") version "8.10.0"
}

spotless {
    format("projectFiles") {
        target("*.gradle.kts", "*/build.gradle.kts", "*.md", ".gitignore")
        trimTrailingWhitespace()
        endWithNewline()
    }
    java {
        target("render3dfw-*/src/**/*.java")
        palantirJavaFormat("2.96.0")
        removeUnusedImports()
        forbidWildcardImports()
        formatAnnotations()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

allprojects {
    group = "dev.vriege.render3dfw"
    version = providers.gradleProperty("releaseVersion").orElse(providers.gradleProperty("mod_version")).get()
}

tasks.named("check") {
    dependsOn("spotlessCheck")
    dependsOn(subprojects.map { it.tasks.matching { task -> task.name == "check" } })
}

subprojects {
    pluginManager.withPlugin("maven-publish") {
        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/melon-trick/Render3D")
                    credentials {
                        username = providers.environmentVariable("GITHUB_ACTOR").orNull
                        password = providers.environmentVariable("GITHUB_TOKEN").orNull
                    }
                }
                maven {
                    name = "Validation"
                    url = uri(providers.gradleProperty("validationRepository")
                        .orElse(rootProject.layout.buildDirectory.dir("repository").map { it.asFile.toURI().toString() }).get())
                }
            }
        }
    }

    pluginManager.withPlugin("java") {
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_25
            targetCompatibility = JavaVersion.VERSION_25
            withSourcesJar()
        }

        dependencies {
            add("testImplementation", platform("org.junit:junit-bom:5.13.4"))
            add("testImplementation", "org.junit.jupiter:junit-jupiter")
            add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
        }

        tasks.withType<JavaCompile>().configureEach {
            options.release = 25
            options.encoding = "UTF-8"
            options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            testLogging {
                events("failed", "skipped")
                exceptionFormat = TestExceptionFormat.FULL
            }
        }
    }

}
