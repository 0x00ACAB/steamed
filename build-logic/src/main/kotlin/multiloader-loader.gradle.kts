plugins {
    id("multiloader-common")
}

val commonPath = ":common"

configurations {
    create("commonJava") {
        isCanBeResolved = true
    }
    create("commonResources") {
        isCanBeResolved = true
    }
}

dependencies {
    add("commonJava", project(path = commonPath, configuration = "commonJava"))
    add("commonResources", project(path = commonPath, configuration = "commonResources"))
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(configurations["commonJava"])
    source(configurations["commonJava"])
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin") {
    dependsOn(configurations["commonJava"])
    source(configurations["commonJava"])
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(configurations["commonResources"])
    from(configurations["commonResources"])

    val metadata = listOf(
        "mod_version", "mod_name", "mod_description", "mod_authors",
        "mod_license", "mod_sources_url", "mod_issues_url", "mod_contact_email",
    ).associateWith { providers.gradleProperty(it).get() }
    inputs.properties(metadata)
    filesMatching(listOf("fabric.mod.json", "META-INF/neoforge.mods.toml")) {
        expand(metadata)
    }
}
