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

val steamworksJar: Configuration by configurations.creating

dependencies {
    implementation(steamworks)
    steamworksJar(steamworks) { isTransitive = false }

    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Packed in rather than jarJar'd: NeoForge loads jarJar'd libraries in a separate class loader from the mod, and
// SteamNatives has to share SteamAPI's loader for JNI to bind.
tasks.jar {
    from(steamworksJar.elements.map { jars -> jars.map { zipTree(it) } }) { exclude("META-INF/**") }
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
