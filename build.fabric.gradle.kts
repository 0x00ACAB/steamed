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
base.archivesName = "steamed-fabric"

sourceSets.main {
    kotlin.exclude("xyz/kkaii/steamed/neoforge/**", "xyz/kkaii/steamed/quilt/**", "xyz/kkaii/steamed/forge/**")
}

java.toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
kotlin.jvmToolchain(javaVersion)

repositories {
    maven("https://jitpack.io")
}

dependencies {
    minecraft("com.mojang:minecraft:$mc")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${prop("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric_api")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${prop("deps.flk")}")

    implementation(steamworks)
    include(steamworks)

    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

loom {
    runConfigs.all {
        runDirectory = rootProject.file("run/${sc.current.project}")
    }
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
        "refmap" to "",
        "entry_main" to "xyz.kkaii.steamed.fabric.SteamedFabric",
        "entry_prelaunch" to "xyz.kkaii.steamed.fabric.SteamPreLaunchFabric",
        "dep_fabricloader" to prop("meta.fabricloader"),
        "dep_minecraft" to prop("meta.minecraft_fabric"),
        "dep_flk" to prop("meta.flk"),
        "platform_helper" to "xyz.kkaii.steamed.fabric.FabricPlatformHelper",
    )
    inputs.properties(metadata)
    filesMatching(listOf("fabric.mod.json", "steamed.mixins.json", "META-INF/services/*")) { expand(metadata) }
    exclude("META-INF/neoforge.mods.toml", "META-INF/mods.toml", "pack.mcmeta")
}

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) { rename { "${it}_steamed" } }
    from(rootProject.file("THIRD_PARTY_NOTICES"))
}
