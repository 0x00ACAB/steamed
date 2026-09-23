plugins {
    id("multiloader-loader")
    id("net.fabricmc.fabric-loom")
}

dependencies {
    minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")

    implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
    implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
    implementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")

    include("com.code-disaster.steamworks4j:steamworks4j:1.10.0")
    include("com.code-disaster.steamworks4j:steamworks4j-lwjgl3:1.10.0")
    include("org.bitlet:weupnp:0.1.2")

    implementation("com.code-disaster.steamworks4j:steamworks4j:1.10.0")
    implementation("com.code-disaster.steamworks4j:steamworks4j-lwjgl3:1.10.0")
    implementation("org.bitlet:weupnp:0.1.2")
}

val loaderAttribute = Attribute.of("gay.git.kai.steamed.loader", String::class.java)
listOf("apiElements", "runtimeElements").forEach { variant ->
    configurations.named(variant) {
        attributes { attribute(loaderAttribute, "fabric") }
    }
}
sourceSets.configureEach {
    listOf(compileClasspathConfigurationName, runtimeClasspathConfigurationName).forEach { variant ->
        configurations.named(variant) {
            attributes { attribute(loaderAttribute, "fabric") }
        }
    }
}
