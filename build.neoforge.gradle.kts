import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

plugins {
    kotlin("jvm")
    id("net.neoforged.moddev")
}

fun prop(key: String): String = sc.properties[key]
val mc = sc.current.version
val javaVersion = prop("mod.java").toInt()
val steamworks = "com.code-disaster.steamworks4j:steamworks4j:${prop("deps.steamworks4j")}"

version = "${prop("mod.version")}+$mc"
group = prop("mod.group")
base.archivesName = "steamed-neoforge"

sourceSets.main {
    kotlin.exclude("xyz/kkaii/steamed/fabric/**", "xyz/kkaii/steamed/quilt/**")
}

java.toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
kotlin.jvmToolchain(javaVersion)

repositories {
    maven("https://jitpack.io")
    exclusiveContent {
        forRepository { maven("https://thedarkcolour.github.io/KotlinForForge/") { name = "Kotlin for Forge" } }
        filter { includeGroup("thedarkcolour") }
    }
}

neoForge {
    version = prop("deps.neo")

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

val steamworksJar: Configuration = configurations.create("steamworksJar")
// Dev runs only: the jar declares KFF as a dependency, which NeoForge enforces even in runClient.
val devRuntime: Configuration = configurations.create("devRuntime")
configurations.runtimeClasspath { extendsFrom(devRuntime) }

dependencies {
    implementation(steamworks)
    steamworksJar(steamworks) { isTransitive = false }
    devRuntime("thedarkcolour:kotlinforforge-neoforge:${prop("deps.kff")}")

    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Packed in rather than jarJar'd: NeoForge loads jarJar'd libraries in a separate class loader from the mod, and
// SteamNatives has to share SteamAPI's loader for JNI to bind.
tasks.jar {
    from(steamworksJar.elements.map { jars -> jars.map { zipTree(it) } }) { exclude("META-INF/**") }
}

// 1.21.1 dev runs load classpath libraries in a different layer from the mod, so JNI can't bind SteamAPI's natives
// (the same reason the jar packs steamworks4j in). Unpack it next to the mod's classes for runClient only.
if (sc.current.parsed < "26.1") {
    val unpackSteamworks = tasks.register<Sync>("unpackSteamworks") {
        from(steamworksJar.elements.map { jars -> jars.map { zipTree(it) } }) { exclude("META-INF/**") }
        into(layout.buildDirectory.dir("steamworks-dev"))
    }
    sourceSets.main { output.dir(mapOf("builtBy" to unpackSteamworks), layout.buildDirectory.dir("steamworks-dev")) }
    tasks.jar { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
}

tasks.test {
    useJUnitPlatform()
}

tasks.named("createMinecraftArtifacts") {
    dependsOn("stonecutterGenerate")
}

// Two NeoForge nodes decompiling Minecraft at once run out of memory.
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
        "dep_neoforge" to prop("meta.neo"),
        "dep_minecraft" to prop("meta.minecraft_neoforge"),
        "dep_kff" to prop("meta.kff"),
        "platform_helper" to "xyz.kkaii.steamed.neoforge.NeoForgePlatformHelper",
    )
    inputs.properties(metadata)
    filesMatching(listOf("META-INF/neoforge.mods.toml", "steamed.mixins.json", "META-INF/services/*")) {
        expand(metadata)
    }
    exclude("fabric.mod.json")
}

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) { rename { "${it}_steamed" } }
    from(rootProject.file("THIRD_PARTY_NOTICES"))
}
