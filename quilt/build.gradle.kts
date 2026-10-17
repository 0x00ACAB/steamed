plugins {
    id("multiloader-loader")
    id("net.fabricmc.fabric-loom")
}

dependencies {
    minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")

    // Quilt Loader reads fabric.mod.json directly and implements the same net.fabricmc.loader.api
    // surface, so this runs unmodified. QSL/Quilted Fabric API stop at 1.21.1/1.20.1, so plain
    // Fabric API it is: nothing imports it, but its resource loader is what loads our assets.
    implementation("org.quiltmc:quilt-loader:${providers.gradleProperty("quilt_loader_version").get()}")
    // quilt-loader's POM declares no dependencies; its libraries are only listed in its
    // quilt_installer.json, which Loom doesn't read. Mixin/ASM/MixinExtras are newer than Loom's.
    runtimeOnly("org.quiltmc:quilt-json5:1.0.4+final")
    runtimeOnly("org.quiltmc:quilt-config:1.3.3")
    runtimeOnly("net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7")
    runtimeOnly("io.github.llamalad7:mixinextras-fabric:0.5.5")
    listOf("asm", "asm-analysis", "asm-commons", "asm-tree", "asm-util").forEach {
        runtimeOnly("org.ow2.asm:$it:9.10.1")
    }
    implementation(
        "net.fabricmc:fabric-language-kotlin:${
            providers.gradleProperty("quilt_fabric_kotlin_version").get()
        }"
    )

    runtimeOnly("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")

    include("com.code-disaster.steamworks4j:steamworks4j:1.10.0")

    implementation("com.code-disaster.steamworks4j:steamworks4j:1.10.0")
}

// FLK pulls in real fabric-loader transitively, which redefines the same net.fabricmc.loader.impl.*
// package as quilt-loader with a different jar signer: SecurityException at launch. It stays on the
// compile classpath (it brings Mixin/MixinExtras), but quilt-loader replaces it at runtime.
configurations.runtimeClasspath {
    exclude(group = "net.fabricmc", module = "fabric-loader")
}

loom.mods.register("steamed") {
    sourceSet(sourceSets.main.get())
}

// Loom only reads fabric-installer.json, so it would launch Fabric's Knot; quilt-loader's is elsewhere.
// It also only sets fabric.* properties, which Quilt ignores in favour of its own loader.* ones.
loom.runs.named("client") {
    mainClass.set("org.quiltmc.loader.impl.launch.knot.KnotClient")
    property("loader.development", "true")
    val output = sourceSets.main.get().output
    property(
        "loader.classPathGroups",
        (output.classesDirs.files + output.resourcesDir!!).joinToString(File.pathSeparator)
    )
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
