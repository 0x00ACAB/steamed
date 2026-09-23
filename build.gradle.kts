plugins {
    id("net.fabricmc.fabric-loom") apply false
    id("net.neoforged.moddev") apply false
}

allprojects {
    version = providers.gradleProperty("mod_version").get()
    group = providers.gradleProperty("maven_group").get()
}

subprojects {
    repositories {
        maven("https://jitpack.io")
    }
}
