plugins {
    id("multiloader-loader")
    id("net.fabricmc.fabric-loom")
}

dependencies {
    minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")

    // Quilt Loader reads fabric.mod.json directly and implements the same net.fabricmc.loader.api
    // surface, so a plain Fabric-targeting mod (this one doesn't use fabric-api itself, just the
    // loader's entrypoint/language-adapter mechanism) runs unmodified - no QSL/QFAPI needed, which
    // is good, since QSL hasn't been updated for a current Minecraft version in a while.
    implementation("org.quiltmc:quilt-loader:${providers.gradleProperty("quilt_loader_version").get()}") {
        // quilt-loader pulls in real fabric-loader transitively, which redefines the same
        // net.fabricmc.loader.impl.* package with mismatched jar signers, causing a
        // SecurityException at runtime. Quilt's own implementation of that package is enough.
        exclude(group = "net.fabricmc", module = "fabric-loader")
    }
    implementation(
        "net.fabricmc:fabric-language-kotlin:${
            providers.gradleProperty("quilt_fabric_kotlin_version").get()
        }"
    )

    include("com.code-disaster.steamworks4j:steamworks4j:1.10.0")
    include("com.code-disaster.steamworks4j:steamworks4j-lwjgl3:1.10.0")

    implementation("com.code-disaster.steamworks4j:steamworks4j:1.10.0")
    implementation("com.code-disaster.steamworks4j:steamworks4j-lwjgl3:1.10.0")
}

repositories {
    maven {
        name = "Quilt"
        url = uri("https://maven.quiltmc.org/repository/release/")
    }
}

val loaderAttribute = Attribute.of("xyz.kkaii.steamed.loader", String::class.java)
listOf("apiElements", "runtimeElements").forEach { variant ->
    configurations.named(variant) {
        attributes { attribute(loaderAttribute, "quilt") }
    }
}
sourceSets.configureEach {
    listOf(compileClasspathConfigurationName, runtimeClasspathConfigurationName).forEach { variant ->
        configurations.named(variant) {
            attributes { attribute(loaderAttribute, "quilt") }
        }
    }
}
