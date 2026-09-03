import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    base
    id("com.diffplug.spotless") version "8.10.0"
    id("net.fabricmc.fabric-loom") version "1.17.20" apply false
}

spotless {
    format("projectFiles") {
        target("*.gradle.kts", "*/build.gradle.kts", "*.md", ".gitignore")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

allprojects {
    group = "dev.melontrick.render3dfw"
    version = providers.gradleProperty("mod_version").get()
}

subprojects {
    apply(plugin = "com.diffplug.spotless")

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

    extensions.configure<SpotlessExtension> {
        java {
            palantirJavaFormat("2.96.0").formatJavadoc(false)
            removeUnusedImports()
            forbidWildcardImports()
            formatAnnotations()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
}
