plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.10.5"
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
}

group = "com.palg"
version = "2.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("io.github.microutils:kotlin-logging:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.5.13")
    implementation("com.google.code.gson:gson:2.10.1")

    intellijPlatform {
        intellijIdeaCommunity("2024.1")
        bundledPlugin("com.intellij.java")
    }
}

kotlin {
    jvmToolchain(17)
}

intellijPlatform {
    pluginConfiguration {
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "241"
            untilBuild = provider { null }
        }
    }
    buildSearchableOptions = false
    pluginVerification {
        ides {
            create("IC", "2024.1")
        }
    }
}