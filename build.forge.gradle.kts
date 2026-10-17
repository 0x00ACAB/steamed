import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    kotlin("jvm")
    id("net.neoforged.moddev.legacyforge")
}

fun prop(key: String): String = sc.properties[key]
val mc = sc.current.version
val javaVersion = prop("mod.java").toInt()
val steamworks = "com.code-disaster.steamworks4j:steamworks4j:${prop("deps.steamworks4j")}"
val mixinExtras = prop("deps.mixinextras")

version = "${prop("mod.version")}+$mc"
group = prop("mod.group")
base.archivesName = "steamed-forge"

sourceSets.main {
    kotlin.exclude("xyz/kkaii/steamed/fabric/**", "xyz/kkaii/steamed/neoforge/**", "xyz/kkaii/steamed/quilt/**")
}

java.toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
kotlin {
    jvmToolchain(javaVersion)
    // KFF 4.12.0 provides the stdlib at runtime, and it's Kotlin 2.2.
    compilerOptions.apiVersion = KotlinVersion.KOTLIN_2_2
}

repositories {
    maven("https://jitpack.io")
    exclusiveContent {
        forRepository { maven("https://thedarkcolour.github.io/KotlinForForge/") { name = "Kotlin for Forge" } }
        filter { includeGroup("thedarkcolour") }
    }
}

legacyForge {
    version = prop("deps.mcforge")

    runs {
        register("client") {
            client()
            gameDirectory = rootProject.file("run/${sc.current.project}")
        }
    }

    mods {
        register("steamed") {
            sourceSet(sourceSets.main.get())
        }
    }
}

mixin {
    add(sourceSets.main.get(), "steamed.refmap.json")
    config("steamed.mixins.json")
}

val steamworksJar: Configuration = configurations.create("steamworksJar")

dependencies {
    implementation(steamworks)
    steamworksJar(steamworks) { isTransitive = false }
    modRuntimeOnly("thedarkcolour:kotlinforforge:${prop("deps.kff_forge")}")

    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
    compileOnly(annotationProcessor("io.github.llamalad7:mixinextras-common:$mixinExtras")!!)
    implementation(jarJar("io.github.llamalad7:mixinextras-forge:$mixinExtras")!!)

    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Packed in rather than jarJar'd, as on NeoForge: SteamNatives has to share SteamAPI's class loader for JNI to bind.
tasks.jar {
    from(steamworksJar.elements.map { jars -> jars.map { zipTree(it) } }) { exclude("META-INF/**") }
    // Forge 1.20.1 finds mixin configs through the manifest, not mods.toml.
    manifest.attributes("MixinConfigs" to "steamed.mixins.json")
}

// Dev runs load classpath libraries in a different layer from the mod, so JNI can't bind SteamAPI's natives (the same
// reason the jar packs steamworks4j in). Unpack it next to the mod's classes for runClient only.
val unpackSteamworks = tasks.register<Sync>("unpackSteamworks") {
    from(steamworksJar.elements.map { jars -> jars.map { zipTree(it) } }) { exclude("META-INF/**") }
    into(layout.buildDirectory.dir("steamworks-dev"))
}
sourceSets.main { output.dir(mapOf("builtBy" to unpackSteamworks), layout.buildDirectory.dir("steamworks-dev")) }
tasks.jar { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }

tasks.test {
    useJUnitPlatform()
}

tasks.named("createMinecraftArtifacts") {
    dependsOn("stonecutterGenerate")
}

// Shared with the NeoForge nodes: two decompiles at once run out of memory.
interface NeoForgeMutex : BuildService<BuildServiceParameters.None>

val mutex = gradle.sharedServices.registerIfAbsent("createMinecraftArtifactsMutex", NeoForgeMutex::class.java) {
    maxParallelUsages.set(1)
}
tasks.named { it == "createMinecraftArtifacts" }.configureEach { usesService(mutex) }

val mixins = sc.properties.rawOrNull("mod", "mixins")!!.asList().joinToString(",\n    ") { "\"$it\"" }

tasks.processResources {
    val metadata = listOf(
        "version", "name", "description", "authors", "license", "sources_url", "issues_url", "contact_email",
    ).associate { "mod_$it" to prop("mod.$it") } + mapOf(
        "java" to javaVersion.toString(),
        "mixins" to mixins,
        "refmap" to "\n  \"refmap\": \"steamed.refmap.json\",",
        // Forge 1.20.1 skips a mod's resources without pack metadata; 15 is 1.20.1's resource pack format.
        "pack_format" to "15",
        "dep_forge" to prop("meta.mcforge"),
        "dep_minecraft" to prop("meta.minecraft_forge"),
        "dep_kff" to prop("meta.kff_forge"),
        "platform_helper" to "xyz.kkaii.steamed.forge.ForgePlatformHelper",
    )
    inputs.properties(metadata)
    filesMatching(listOf("META-INF/mods.toml", "steamed.mixins.json", "pack.mcmeta", "META-INF/services/*")) { expand(metadata) }
    exclude("fabric.mod.json", "META-INF/neoforge.mods.toml")
}

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) { rename { "${it}_steamed" } }
    from(rootProject.file("THIRD_PARTY_NOTICES"))
}
