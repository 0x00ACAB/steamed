plugins {
    id("multiloader-common")
    id("net.neoforged.moddev")
}

neoForge {
    neoFormVersion = providers.gradleProperty("neo_form_version").get()
}

dependencies {
    // Fabric and NeoForge both bundle Mixin and MixinExtras at these versions
    // (confirmed via this project's own runtime logs), so it's safe to use them in common.
    compileOnly("net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7")
    compileOnly(annotationProcessor("io.github.llamalad7:mixinextras-common:0.5.5")!!)

    implementation("com.code-disaster.steamworks4j:steamworks4j:1.10.0")
    implementation("com.code-disaster.steamworks4j:steamworks4j-lwjgl3:1.10.0")
}

configurations {
    create("commonJava") {
        isCanBeResolved = false
        isCanBeConsumed = true
    }
    create("commonResources") {
        isCanBeResolved = false
        isCanBeConsumed = true
    }
}

artifacts {
    add("commonJava", sourceSets.main.get().java.sourceDirectories.singleFile)
    sourceSets.main.get().kotlin.sourceDirectories.forEach { dir -> add("commonJava", dir) }
    add("commonResources", sourceSets.main.get().resources.sourceDirectories.singleFile)
}

// Disambiguates common's outgoing variants when both :fabric and :neoforge depend on it
// with different resolved attributes.
val loaderAttribute = Attribute.of("xyz.kkaii.steamed.loader", String::class.java)
listOf("apiElements", "runtimeElements").forEach { variant ->
    configurations.named(variant) {
        attributes { attribute(loaderAttribute, "common") }
    }
}
sourceSets.configureEach {
    listOf(compileClasspathConfigurationName, runtimeClasspathConfigurationName).forEach { variant ->
        configurations.named(variant) {
            attributes { attribute(loaderAttribute, "common") }
        }
    }
}
