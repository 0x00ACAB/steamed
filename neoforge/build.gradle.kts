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

dependencies {
    // TODO(kai): jarJar these for production packaging once dev-environment native loading is verified
    implementation("com.code-disaster.steamworks4j:steamworks4j:1.10.0")
    implementation("com.code-disaster.steamworks4j:steamworks4j-lwjgl3:1.10.0")
    implementation("org.bitlet:weupnp:0.1.2")
}

val loaderAttribute = Attribute.of("gay.git.kai.steamed.loader", String::class.java)
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
