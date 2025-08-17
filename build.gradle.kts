plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.13.3"
    id("org.jetbrains.kotlin.jvm") version "1.8.20"
}

version = "1.7"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.microutils:kotlin-logging:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.5.13")
    implementation("com.google.code.gson:gson:2.10.1")
}

intellij {
    version.set("2022.2.5")
    updateSinceUntilBuild.set(false)
}

tasks {
    buildSearchableOptions {
        enabled = false
    }

    patchPluginXml {
        version.set("${project.version}")
        sinceBuild.set("222")
    }

    compileKotlin {
        kotlinOptions.jvmTarget = "17"
    }

    runPluginVerifier {
        ideVersions.set(
            listOf("2022.1", "2025.2"))
    }
}


