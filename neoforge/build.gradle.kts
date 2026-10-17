plugins {
    id("multiloader-loader")
    id("net.neoforged.moddev")
}

neoForge {
    version = providers.gradleProperty("neoforge_version").get()

    runs {
        create("client") {
            client()
        }
    }

    mods {
        create("steamed") {
            sourceSet(sourceSets.main.get())
        }
    }
}

val steamworks: Configuration by configurations.creating

dependencies {
    implementation("com.code-disaster.steamworks4j:steamworks4j:1.10.0")
    steamworks("com.code-disaster.steamworks4j:steamworks4j:1.10.0") { isTransitive = false }
}

// Packed in rather than jarJar'd: NeoForge loads jarJar'd libraries in a separate class loader from the mod, and
// SteamNatives has to share SteamAPI's loader for JNI to bind.
tasks.jar {
    from(steamworks.elements.map { jars -> jars.map { zipTree(it) } }) { exclude("META-INF/**") }
}

val loaderAttribute = Attribute.of("xyz.kkaii.steamed.loader", String::class.java)
listOf("apiElements", "runtimeElements").forEach { variant ->
    configurations.named(variant) {
        attributes { attribute(loaderAttribute, "neoforge") }
    }
}
sourceSets.configureEach {
    listOf(compileClasspathConfigurationName, runtimeClasspathConfigurationName).forEach { variant ->
        configurations.named(variant) {
            attributes { attribute(loaderAttribute, "neoforge") }
        }
    }
}
