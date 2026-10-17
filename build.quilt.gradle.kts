plugins {
    kotlin("jvm")
    id("dev.kikugie.loom-back-compat")
}

fun prop(key: String): String = sc.properties[key]
val mc = sc.current.version
val javaVersion = prop("mod.java").toInt()
val steamworks = "com.code-disaster.steamworks4j:steamworks4j:${prop("deps.steamworks4j")}"

version = "${prop("mod.version")}+$mc"
group = prop("mod.group")
base.archivesName = "steamed-quilt"

sourceSets.main {
    kotlin.exclude("xyz/kkaii/steamed/fabric/**", "xyz/kkaii/steamed/neoforge/**")
}

java.toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
kotlin.jvmToolchain(javaVersion)

repositories {
    maven("https://jitpack.io")
    maven {
        name = "Quilt"
        url = uri("https://maven.quiltmc.org/repository/release/")
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$mc")
    loomx.applyMojangMappings()

    // Quilt Loader reads fabric.mod.json directly and implements the same net.fabricmc.loader.api
    // surface, so this runs unmodified. QSL/Quilted Fabric API stop at 1.21.1/1.20.1, so plain
    // Fabric API it is: nothing imports it, but its resource loader is what loads our assets.
    implementation("org.quiltmc:quilt-loader:${prop("deps.quilt_loader")}")
    // quilt-loader's POM declares no dependencies; its libraries are only listed in its
    // quilt_installer.json, which Loom doesn't read. Mixin/ASM/MixinExtras are newer than Loom's.
    runtimeOnly("org.quiltmc:quilt-json5:1.0.4+final")
    runtimeOnly("org.quiltmc:quilt-config:1.3.3")
    runtimeOnly("net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7")
    runtimeOnly("io.github.llamalad7:mixinextras-fabric:0.5.5")
    listOf("asm", "asm-analysis", "asm-commons", "asm-tree", "asm-util").forEach {
        runtimeOnly("org.ow2.asm:$it:9.10.1")
    }
    implementation("net.fabricmc:fabric-language-kotlin:${prop("deps.flk_quilt")}")

    runtimeOnly("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric_api")}")

    implementation(steamworks)
    include(steamworks)

    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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

loom.runConfigs.all {
    runDirectory = rootProject.file("run/${sc.current.project}")
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

tasks.test {
    useJUnitPlatform()
}

val mixins = sc.properties.rawOrNull("mod", "mixins")!!.asList().joinToString(",\n    ") { "\"$it\"" }

tasks.processResources {
    val metadata = listOf(
        "version", "name", "description", "authors", "license", "sources_url", "issues_url", "contact_email",
    ).associate { "mod_$it" to prop("mod.$it") } + mapOf(
        "java" to javaVersion.toString(),
        "mixins" to mixins,
        "entry_main" to "xyz.kkaii.steamed.quilt.SteamedQuilt",
        "entry_prelaunch" to "xyz.kkaii.steamed.quilt.SteamPreLaunchQuilt",
        "dep_fabricloader" to prop("meta.fabricloader_quilt"),
        "dep_minecraft" to prop("meta.minecraft_fabric"),
        "dep_flk" to prop("meta.flk"),
        "platform_helper" to "xyz.kkaii.steamed.quilt.QuiltPlatformHelper",
    )
    inputs.properties(metadata)
    filesMatching(listOf("fabric.mod.json", "steamed.mixins.json", "META-INF/services/*")) { expand(metadata) }
    exclude("META-INF/neoforge.mods.toml")
}

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) { rename { "${it}_steamed" } }
    from(rootProject.file("THIRD_PARTY_NOTICES"))
}
