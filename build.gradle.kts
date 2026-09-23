import org.gradle.api.internal.provider.ValueSupplier.ValueProducer.task
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("net.fabricmc.fabric-loom")
	`maven-publish`
	id("org.jetbrains.kotlin.jvm") version "2.4.20"
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()



repositories {
	maven("https://maven.terraformersmc.com/")
	maven("https://jitpack.io")
}

dependencies {
	minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")

	implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")

	implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
	implementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")

	implementation("com.terraformersmc:modmenu:${providers.gradleProperty("modmenu_version").get()}")

	include("com.code-disaster.steamworks4j:steamworks4j:1.10.0")
	include("com.code-disaster.steamworks4j:steamworks4j-lwjgl3:1.10.0")

	implementation("com.code-disaster.steamworks4j:steamworks4j:1.10.0")
	implementation("com.code-disaster.steamworks4j:steamworks4j-lwjgl3:1.10.0")

	implementation("org.bitlet:weupnp:0.1.2")
	include("org.bitlet:weupnp:0.1.2")
}

tasks.processResources {
	val version = version
	inputs.property("version", version)

	filesMatching("fabric.mod.json") {
		expand("version" to version)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 25
}

kotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_25
	}
}

java {
	// Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
	// if it is present.
	// If you remove this line, sources will not be generated.
	withSourcesJar()

	sourceCompatibility = JavaVersion.VERSION_25
	targetCompatibility = JavaVersion.VERSION_25
}

tasks.jar {
	val projectName = project.name
	inputs.property("projectName", projectName)

	from("LICENSE") {
		rename { "${it}_$projectName" }
	}
}

// configure the maven publication
publishing {
	publications {
		register<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}

	// See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
	repositories {
		// Add repositories to publish to here.
		// Notice: This block does NOT have the same function as the block in the top level.
		// The repositories here will be used for publishing your artifact, not for
		// retrieving dependencies.
	}
}
