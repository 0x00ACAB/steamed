# Stonecutter Restructure and 1.21.1 Port Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild Steamed on Stonecutter so one source tree builds 26.2 (Fabric, NeoForge, Quilt) and 1.21.1 (Fabric, NeoForge), and ship all five jars as 0.2.0.

**Architecture:** Phase A flattens `common`/`fabric`/`neoforge`/`quilt` into one `src/` tree with Stonecutter nodes, with the 26.2 jars matching 0.1.4. Phase B moves every 26.x-only API call behind a small per-version `compat` package (one file per Minecraft version, compiled by a whole-file Stonecutter condition), adds the 1.21.1 nodes, writes the 1.21.1 compat file and mixins, and prepares the 0.2.0 release.

**Tech Stack:** Stonecutter 0.9.8, loom-back-compat 0.4 (Fabric Loom 1.18.2), ModDevGradle 2.0.147, Kotlin 2.4.20, Java 21/25, steamworks4j 1.10.0, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-17-stonecutter-1.21.1-design.md`

## Global Constraints

- Nodes: `1.21.1-fabric`, `1.21.1-neoforge`, `26.2-fabric`, `26.2-neoforge`, `26.2-quilt`. Checked-in active node: `26.2-fabric`
- Java toolchain: 21 for 1.21.1, 25 for 26.x. Kotlin 2.4.20 everywhere
- 26.2-fabric: Fabric Loader 0.19.5, Fabric API 0.161.0+26.2, FLK 1.14.1+kotlin.2.4.20
- 26.2-quilt: Quilt Loader 0.31.0-beta.4 (dev), Fabric API 0.161.0+26.2, FLK 1.13.14+kotlin.2.4.20
- 26.2-neoforge: NeoForge 26.2.0.88, KFF >=6.4
- 1.21.1-fabric: Fabric Loader 0.19.5, Fabric API 0.116.17+1.21.1, FLK 1.14.1+kotlin.2.4.20
- 1.21.1-neoforge: NeoForge 21.1.262, KFF >=5.12
- Out of scope: Quilt on 1.21.1, ViaFabric, cross-version play, anything needing a second Steam account
- Jars: `steamed-<loader>-<version>+<mc>.jar`. Modrinth versions `<version>+<mc>-<loader>`, beta; game versions `26.2` for 26.2 nodes, `1.21` and `1.21.1` for 1.21.1 nodes
- Mod metadata `version` (fabric.mod.json, neoforge.mods.toml, what `IPlatformHelper.modVersion()` returns and what lobbies publish as `steamed`) is the plain mod version, never `+<mc>`
- Steam relays, `SteamAuthHandshake`, voice and web-map tunnels, `SteamNatives`, `SteamPlayers`' cache logic, `WebMapConfig`/`VoicePorts`/`WebMapList` are not split by version
- Before every commit: `grep -q 'stonecutter active "26.2-fabric"' stonecutter.gradle.kts` must succeed. If you switched nodes to work on 1.21.1, switch back first (`./gradlew "Set active project to 26.2-fabric"`; the exact task name is printed by `./gradlew tasks --group stonecutter`)
- Keep comments minimal; Kotlin for logic, Java only for mixins; SLF4J logging (`CLAUDE.md` conventions)
- Commits: GPG-signed, author `0x00ACAB` noreply address (already the configured git user), ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. No pushes or tags without the user's go-ahead
- Scratch space: `SCRATCH=/tmp/claude-1000/-data-0-kai-projects-java-steamed/b5baefae-5dc2-46dd-bf0c-c18f33d29617/scratchpad`

## Deviations from the spec (decided in this plan, flagged for review)

1. **Loader packages stay where they are** (`xyz.kkaii.steamed.fabric`, `.neoforge`, `.quilt`), under
   `src/main/kotlin/xyz/kkaii/steamed/{fabric,neoforge,quilt}/`, not `platform/{...}`. Moving them renames classes in
   the jar and in `fabric.mod.json`/the service file, which breaks the spec's own restructure gate ("only version
   strings and the jar name may differ")
2. **Tier 2 goes in compat files, not inline `//? if` blocks.** There are ~40 call sites of moved APIs (chat, screen,
   toast, render signatures). One `compat/Compat26.kt` and one `compat/Compat1_21_1.kt` (same top-level names, each
   wrapped in a whole-file condition, as Multicutter's `Util26_2`/`Util1_21_1` do) keep the screens and `Steamed.kt`
   free of conditionals. Inline `//?` blocks remain only in mixins
3. **Invite screen heads draw through the face renderer on 26.2 too**, instead of a `PlayerSprite` text component, so
   both versions share one code path. `SteamPlayers.head()` is deleted
4. **There are 9 mixins today, not 8** (`SplashManagerMixin`). 1.21.1 also needs three new ones: `PlayerRendererMixin`
   (spec'd), `PlayerListMixin` (1.21.1's player cap is a `PlayerList` field, not `IntegratedServer.getMaxPlayers()`)
   and `MinecraftAccessor` (1.21.1's LWJGL has no `glfwGetWindowTitle`)
5. **The Modrinth upload loop moves to `.github/scripts/modrinth.sh`** with a `DRY_RUN=1` mode, so it can be tested
   locally against the built jars. `build.yml` calls it

## Review Focus

1. **Mixed-version friend lobbies.** A 1.21.1 player looking at a 26.2 friend's lobby must see the "different
   Minecraft" greying, not "different Steamed version". That holds only if all five jars report the same plain mod
   version. Pinned by the metadata check in Task 10
2. **More than 8 players on a 1.21.1 host.** 1.21.1 caps logins with `PlayerList.maxPlayers` (a field read in
   `canPlayerLogin`), so a getter-only fix would show "max 16" in `/list` and still reject the 9th joiner. Pinned by
   the field-read assertion in Task 9 and the `/list` check in Task 11
3. **"Open to LAN" after a Steam lobby on 1.21.1.** `publishingForSteam` must gate the loopback bind and the skipped
   LAN broadcast on the 1.21.1 `publishServer(GameType, boolean, int)` overload too. Pinned by Task 11's LAN check
4. **Heads for friends whose skin can't be fetched on 1.21.1** (offline account, Mojang API down, rate limit). Expect
   the default skin, one request per UUID, no exception per frame. Pinned by Task 8's single-request structure and
   Task 10's log check
5. **A jar on the wrong Minecraft.** A 1.21.1 jar on 1.21.2+ or a 26.2 jar on 1.21.1 must be refused by the loader
   with a clear message, not crash in a mixin. Pinned by the range check in Task 10

---

## Phase A: restructure (26.2 only)

### Task 1: Stonecutter skeleton, flattened sources, 26.2-fabric node

**Files:**
- Create: `settings.gradle.kts` (replace), `stonecutter.gradle.kts`, `stonecutter.properties.toml`, `build.fabric.gradle.kts`
- Move: `common/src/main/**` → `src/main/**`, `common/src/test/**` → `src/test/**`, `fabric/src/main/kotlin/**` → `src/main/kotlin/**`, `neoforge/src/main/kotlin/**` → `src/main/kotlin/**`, `quilt/src/main/kotlin/**` → `src/main/kotlin/**`
- Move: `fabric/src/main/resources/fabric.mod.json` → `src/main/resources/fabric.mod.json` (templated), `neoforge/src/main/resources/META-INF/neoforge.mods.toml` → `src/main/resources/META-INF/neoforge.mods.toml` (templated)
- Create: `src/main/resources/META-INF/services/xyz.kkaii.steamed.platform.IPlatformHelper` (templated)
- Modify: `src/main/resources/steamed.mixins.json` (templated), `gradle.properties`
- Delete: `build.gradle.kts`, `build-logic/`, `common/`, `fabric/`, `neoforge/`, `quilt/` (tracked files only; run dirs are moved first)

**Interfaces:**
- Produces: TOML keys `mod.*`, `deps.*`, `meta.*` (listed in Step 4) that every later build script reads with
  `sc.properties[key]`; resource placeholders `${mod_version}`, `${mod_name}`, `${mod_description}`, `${mod_authors}`,
  `${mod_license}`, `${mod_sources_url}`, `${mod_issues_url}`, `${mod_contact_email}`, `${java}`, `${mixins}`,
  `${entry_main}`, `${entry_prelaunch}`, `${dep_fabricloader}`, `${dep_minecraft}`, `${dep_flk}`,
  `${dep_neoforge}`, `${dep_kff}`, `${platform_helper}`

- [ ] **Step 1: Branch and build the 0.1.4 reference jars**

```bash
cd /data/0/kai/projects/java/steamed
git switch -c stonecutter-1.21.1
SCRATCH=/tmp/claude-1000/-data-0-kai-projects-java-steamed/b5baefae-5dc2-46dd-bf0c-c18f33d29617/scratchpad
git worktree add "$SCRATCH/steamed-0.1.4" v0.1.4
(cd "$SCRATCH/steamed-0.1.4" && ./gradlew build -x test)
mkdir -p "$SCRATCH/ref" && cp "$SCRATCH"/steamed-0.1.4/{fabric,neoforge,quilt}/build/libs/steamed-*-0.1.4.jar "$SCRATCH/ref/"
ls "$SCRATCH/ref"
```

Expected: `steamed-fabric-0.1.4.jar  steamed-neoforge-0.1.4.jar  steamed-quilt-0.1.4.jar`

- [ ] **Step 2: Write the jar diff helper**

`$SCRATCH/jardiff.sh`:

```bash
#!/usr/bin/env bash
# usage: jardiff.sh OLD.jar NEW.jar
set -u
echo "== file list"; diff <(unzip -Z1 "$1" | sort) <(unzip -Z1 "$2" | sort)
for f in fabric.mod.json META-INF/neoforge.mods.toml steamed.mixins.json \
         META-INF/services/xyz.kkaii.steamed.platform.IPlatformHelper; do
  echo "== $f"; diff <(unzip -p "$1" "$f" 2>/dev/null) <(unzip -p "$2" "$f" 2>/dev/null)
done
```

Run: `chmod +x "$SCRATCH/jardiff.sh"`

- [ ] **Step 3: Move the dev run directories out of the module dirs** (they're git-ignored and hold dev worlds and test mods; deleting `fabric/` would lose them)

```bash
mkdir -p run
[ -d fabric/run ] && mv fabric/run run/26.2-fabric
[ -d neoforge/run ] && mv neoforge/run run/26.2-neoforge
[ -d quilt/run ] && mv quilt/run run/26.2-quilt
ls run
```

Expected: `26.2-fabric  26.2-neoforge  26.2-quilt` (plus whatever was already in `run/`).

- [ ] **Step 4: Flatten the sources with `git mv`**

```bash
mkdir -p src/main/kotlin/xyz/kkaii/steamed src/main/resources/META-INF/services
git mv common/src/main/java src/main/java
git mv common/src/main/kotlin/xyz/kkaii/steamed/* src/main/kotlin/xyz/kkaii/steamed/
git mv common/src/main/resources/* src/main/resources/
git mv common/src/test src/test
git mv fabric/src/main/kotlin/xyz/kkaii/steamed/fabric src/main/kotlin/xyz/kkaii/steamed/fabric
git mv neoforge/src/main/kotlin/xyz/kkaii/steamed/neoforge src/main/kotlin/xyz/kkaii/steamed/neoforge
git mv quilt/src/main/kotlin/xyz/kkaii/steamed/quilt src/main/kotlin/xyz/kkaii/steamed/quilt
git mv fabric/src/main/resources/fabric.mod.json src/main/resources/fabric.mod.json
git mv neoforge/src/main/resources/META-INF/neoforge.mods.toml src/main/resources/META-INF/neoforge.mods.toml
git mv fabric/src/main/resources/META-INF/services/xyz.kkaii.steamed.platform.IPlatformHelper \
       src/main/resources/META-INF/services/xyz.kkaii.steamed.platform.IPlatformHelper
git rm -q quilt/src/main/resources/fabric.mod.json \
          neoforge/src/main/resources/META-INF/services/xyz.kkaii.steamed.platform.IPlatformHelper \
          quilt/src/main/resources/META-INF/services/xyz.kkaii.steamed.platform.IPlatformHelper
git rm -rq build.gradle.kts build-logic common fabric neoforge quilt
git status --short | grep -v '^R' | head -40
```

Expected: only `D` lines for build scripts and the two dropped metadata copies; every source file shows as `R`.
Then `ls common fabric neoforge quilt build-logic 2>&1` may still list ignored `build/`/`.gradle/` dirs:
`rm -rf common fabric neoforge quilt build-logic` once `ls */run 2>/dev/null` prints nothing.

- [ ] **Step 5: Template the resources**

`src/main/resources/fabric.mod.json` (the 0.1.4 file with the loader-specific values replaced):

```json
{
  "schemaVersion": 1,
  "id": "steamed",
  "version": "${mod_version}",
  "name": "${mod_name}",
  "description": "${mod_description}",
  "authors": [
    "${mod_authors}"
  ],
  "contact": {
    "sources": "${mod_sources_url}",
    "email": "${mod_contact_email}",
    "issues": "${mod_issues_url}"
  },
  "license": "${mod_license}",
  "icon": "assets/steamed/icon.png",
  "environment": "client",
  "entrypoints": {
    "main": [
      {
        "value": "${entry_main}",
        "adapter": "kotlin"
      }
    ],
    "preLaunch": [
      {
        "value": "${entry_prelaunch}",
        "adapter": "kotlin"
      }
    ],
    "client": [
      {
        "value": "${entry_main}",
        "adapter": "kotlin"
      }
    ]
  },
  "mixins": [
    "steamed.mixins.json"
  ],
  "depends": {
    "fabricloader": "${dep_fabricloader}",
    "minecraft": "${dep_minecraft}",
    "java": ">=${java}",
    "fabric-language-kotlin": "${dep_flk}",
    "fabric-api": "*"
  }
}
```

In `src/main/resources/META-INF/neoforge.mods.toml` change exactly three lines:
`versionRange = "[26.2,)"` → `versionRange = "${dep_neoforge}"`,
`versionRange = "[26.2,26.3)"` → `versionRange = "${dep_minecraft}"`,
`versionRange = "[6.4,)"` → `versionRange = "${dep_kff}"`.

`src/main/resources/META-INF/services/xyz.kkaii.steamed.platform.IPlatformHelper`:

```
${platform_helper}
```

`src/main/resources/steamed.mixins.json`:

```json
{
  "required": true,
  "package": "xyz.kkaii.steamed.mixin",
  "compatibilityLevel": "JAVA_${java}",
  "mixins": [
    ${mixins}
  ],
  "injectors": {
    "defaultRequire": 1
  },
  "overwrites": {
    "requireAnnotations": true
  }
}
```

- [ ] **Step 6: Write `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
    plugins {
        kotlin("jvm") version "2.4.20"
        id("net.neoforged.moddev") version "2.0.147"
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    id("dev.kikugie.loom-back-compat") version "0.4"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        fun match(version: String, vararg loaders: String) {
            for (loader in loaders) version("$version-$loader", version).buildscript("build.$loader.gradle.kts")
        }
        match("26.2", "fabric")
        vcsVersion = "26.2-fabric"
    }
}

rootProject.name = "steamed"
```

- [ ] **Step 7: Write `stonecutter.gradle.kts`**

```kotlin
plugins {
    id("dev.kikugie.stonecutter")
    kotlin("jvm") apply false
    id("net.neoforged.moddev") apply false
}

stonecutter active "26.2-fabric"

stonecutter parameters {
    val (_, loader) = current.project.split('-', limit = 2)
    properties { tags(current.version, loader) }
    constants { match(loader, "fabric", "neoforge", "quilt") }
}
```

- [ ] **Step 8: Write `stonecutter.properties.toml`**

Keys are distinct per loader instead of overriding each other across sections, so no precedence rule matters.

```toml
mod.version = "0.1.4"
mod.group = "xyz.kkaii.steamed"
mod.name = "Steamed"
mod.description = "Provides peer-to-peer networking via Steamworks™"
mod.authors = "Kai Shiraishi"
mod.license = "CC0-1.0"
mod.sources_url = "https://github.com/0x00ACAB/steamed"
mod.issues_url = "https://github.com/0x00ACAB/steamed/issues"
mod.contact_email = "contact@kkaii.xyz"

loomx.loom_version = "1.18.2"

deps.steamworks4j = "1.10.0"
deps.fabric_loader = "0.19.5"
deps.flk = "1.14.1+kotlin.2.4.20"
deps.quilt_loader = "0.31.0-beta.4"
# fabric-language-kotlin 1.14.x requires fabricloader >=0.19.5, which no stable Quilt Loader provides yet
# (0.30.1 provides 0.19.3). 1.13.14 bundles the same Kotlin 2.4.20 stdlib and only requires >=0.16.9.
deps.flk_quilt = "1.13.14+kotlin.2.4.20"

# fabric.mod.json lower bounds
meta.flk = ">=1.13.14"
meta.fabricloader = ">=0.19.5"
meta.fabricloader_quilt = "*"

["26.2"]
mod.java = "25"
mod.mc_releases = ["26.2"]
mod.mixins = ["AvatarRendererMixin", "ChatListenerMixin", "ConnectScreenAccessor", "IntegratedServerMixin",
    "PauseMenuMixin", "PlayerTabOverlayMixin", "SplashManagerMixin", "TitleScreenMixin"]
deps.fabric_api = "0.161.0+26.2"
deps.neo = "26.2.0.88"
meta.minecraft_fabric = "~26.2"
meta.minecraft_neoforge = "[26.2,26.3)"
meta.neo = "[26.2,)"
meta.kff = "[6.4,)"
```

- [ ] **Step 9: Write `build.fabric.gradle.kts`**

```kotlin
plugins {
    kotlin("jvm")
    id("dev.kikugie.loom-back-compat")
}

fun prop(key: String): String = sc.properties[key]
val mc = sc.current.version
val java = prop("mod.java").toInt()
val steamworks = "com.code-disaster.steamworks4j:steamworks4j:${prop("deps.steamworks4j")}"

version = "${prop("mod.version")}+$mc"
group = prop("mod.group")
base.archivesName = "steamed-fabric"

sourceSets.main {
    kotlin.exclude("xyz/kkaii/steamed/neoforge/**", "xyz/kkaii/steamed/quilt/**")
}

java.toolchain.languageVersion = JavaLanguageVersion.of(java)
kotlin.jvmToolchain(java)

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
        runDir = rootProject.file("run/${sc.current.project}").relativeTo(projectDir).path
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
        "java" to java.toString(),
        "mixins" to mixins,
        "entry_main" to "xyz.kkaii.steamed.fabric.SteamedFabric",
        "entry_prelaunch" to "xyz.kkaii.steamed.fabric.SteamPreLaunchFabric",
        "dep_fabricloader" to prop("meta.fabricloader"),
        "dep_minecraft" to prop("meta.minecraft_fabric"),
        "dep_flk" to prop("meta.flk"),
        "platform_helper" to "xyz.kkaii.steamed.fabric.FabricPlatformHelper",
    )
    inputs.properties(metadata)
    filesMatching(listOf("fabric.mod.json", "steamed.mixins.json", "META-INF/services/*")) { expand(metadata) }
    exclude("META-INF/neoforge.mods.toml")
}

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) { rename { "${it}_steamed" } }
    from(rootProject.file("THIRD_PARTY_NOTICES"))
}
```

Note: `sc.properties.rawOrNull(...).asList()` is how Multicutter reads TOML arrays; if 0.9.8's return type
differs, read `rawOrNull` once in a `println` to see its type, and adapt that one line.

- [ ] **Step 10: Raise the Gradle heap** (five nodes, two of them NeoForge, don't fit in 1G)

In `gradle.properties`, replace the whole file with:

```properties
org.gradle.jvmargs=-Xmx4G
org.gradle.parallel=true
# IntelliJ IDEA is not yet fully compatible with configuration cache, see: https://github.com/FabricMC/fabric-loom/issues/1349
org.gradle.configuration-cache=false
```

- [ ] **Step 11: Build and test the node**

Run: `./gradlew :26.2-fabric:build`
Expected: `BUILD SUCCESSFUL`, the four test classes pass, and `versions/26.2-fabric/build/libs/steamed-fabric-0.1.4+26.2.jar` exists (`ls versions/26.2-fabric/build/libs/`).
If plugin resolution fails for loom-back-compat, add the repositories from `$SCRATCH/multicutter/settings.gradle.kts` (clone it with `git clone https://github.com/pajicadvance/multicutter "$SCRATCH/multicutter" && git -C "$SCRATCH/multicutter" checkout bf13a3b` if missing).

- [ ] **Step 12: Diff against 0.1.4**

Run: `"$SCRATCH/jardiff.sh" "$SCRATCH/ref/steamed-fabric-0.1.4.jar" versions/26.2-fabric/build/libs/steamed-fabric-0.1.4+26.2.jar`
Expected: file lists equal except the Kotlin module file (`META-INF/fabric.kotlin_module` → `META-INF/26.2-fabric.kotlin_module`); `fabric.mod.json`, `steamed.mixins.json` and the service file identical apart from a trailing newline. Any other difference is a bug: fix it before going on.

- [ ] **Step 13: Ignore Stonecutter's per-node dirs and commit**

Run `git status --short`. If `versions/` shows anything besides build output, add `versions/*/build/` handling is already covered by `build/`; add `versions/*/.gradle/` to `.gitignore` only if it appears.

```bash
grep -q 'stonecutter active "26.2-fabric"' stonecutter.gradle.kts
git add -A
git commit -S -m "build: move to Stonecutter, 26.2-fabric node

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 2: 26.2-neoforge node

**Files:**
- Create: `build.neoforge.gradle.kts`
- Modify: `settings.gradle.kts` (`match("26.2", "fabric", "neoforge")`)

**Interfaces:**
- Consumes: the TOML keys and resource placeholders from Task 1

- [ ] **Step 1: Add the node to `settings.gradle.kts`**

```kotlin
        match("26.2", "fabric", "neoforge")
```

- [ ] **Step 2: Write `build.neoforge.gradle.kts`**

```kotlin
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

plugins {
    kotlin("jvm")
    id("net.neoforged.moddev")
}

fun prop(key: String): String = sc.properties[key]
val mc = sc.current.version
val java = prop("mod.java").toInt()
val steamworks = "com.code-disaster.steamworks4j:steamworks4j:${prop("deps.steamworks4j")}"

version = "${prop("mod.version")}+$mc"
group = prop("mod.group")
base.archivesName = "steamed-neoforge"

sourceSets.main {
    kotlin.exclude("xyz/kkaii/steamed/fabric/**", "xyz/kkaii/steamed/quilt/**")
}

java.toolchain.languageVersion = JavaLanguageVersion.of(java)
kotlin.jvmToolchain(java)

repositories {
    maven("https://jitpack.io")
}

neoForge {
    version = prop("deps.neoforge")

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
        "java" to java.toString(),
        "mixins" to mixins,
        "dep_neoforge" to prop("meta.neoforge"),
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
```

If the build complains that a declared `interface` can't be used as a build service in a script, move the two
mutex lines into `stonecutter.gradle.kts` (the root project) and register it there with the same name.

- [ ] **Step 3: Build, test, diff**

```bash
./gradlew :26.2-neoforge:build
"$SCRATCH/jardiff.sh" "$SCRATCH/ref/steamed-neoforge-0.1.4.jar" versions/26.2-neoforge/build/libs/steamed-neoforge-0.1.4+26.2.jar
```

Expected: `BUILD SUCCESSFUL`, tests pass; the steamworks4j classes and natives (`com/codedisaster/**`, `*.so`, `*.dll`, `*.dylib`) are in both file lists; only the Kotlin module file name differs; `neoforge.mods.toml` identical.

- [ ] **Step 4: Commit**

```bash
grep -q 'stonecutter active "26.2-fabric"' stonecutter.gradle.kts
git add -A && git commit -S -m "build: 26.2-neoforge node

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 3: 26.2-quilt node

**Files:**
- Create: `build.quilt.gradle.kts`
- Modify: `settings.gradle.kts` (`match("26.2", "fabric", "neoforge", "quilt")`)

**Interfaces:**
- Consumes: TOML keys `deps.quilt_loader`, `deps.flk_quilt`, `meta.fabricloader_quilt` from Task 1

- [ ] **Step 1: Add the node to `settings.gradle.kts`**

```kotlin
        match("26.2", "fabric", "neoforge", "quilt")
```

- [ ] **Step 2: Write `build.quilt.gradle.kts`** (0.1.4's `quilt/build.gradle.kts`, reading the TOML, plus the shared bits)

```kotlin
plugins {
    kotlin("jvm")
    id("dev.kikugie.loom-back-compat")
}

fun prop(key: String): String = sc.properties[key]
val mc = sc.current.version
val java = prop("mod.java").toInt()
val steamworks = "com.code-disaster.steamworks4j:steamworks4j:${prop("deps.steamworks4j")}"

version = "${prop("mod.version")}+$mc"
group = prop("mod.group")
base.archivesName = "steamed-quilt"

sourceSets.main {
    kotlin.exclude("xyz/kkaii/steamed/fabric/**", "xyz/kkaii/steamed/neoforge/**")
}

java.toolchain.languageVersion = JavaLanguageVersion.of(java)
kotlin.jvmToolchain(java)

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
    runDir = rootProject.file("run/${sc.current.project}").relativeTo(projectDir).path
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
        "java" to java.toString(),
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
```

- [ ] **Step 3: Build, test, diff, run**

```bash
./gradlew build
"$SCRATCH/jardiff.sh" "$SCRATCH/ref/steamed-quilt-0.1.4.jar" versions/26.2-quilt/build/libs/steamed-quilt-0.1.4+26.2.jar
```

Expected: all three nodes `BUILD SUCCESSFUL` with tests; quilt diff shows only the Kotlin module file.

Then, with the Steam client running: `./gradlew :26.2-quilt:runClient` in the background, wait for the log line
`Starting steam poller task` and the title screen, then stop it. Expected: no `SecurityException`, no mixin errors
(`grep -iE "mixin.*(error|fail)|SecurityException" run/26.2-quilt/logs/latest.log` prints nothing).

- [ ] **Step 4: Commit**

```bash
grep -q 'stonecutter active "26.2-fabric"' stonecutter.gradle.kts
git add -A && git commit -S -m "build: 26.2-quilt node

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 4: CI, CodeQL, Dependabot and docs for the new layout

**Files:**
- Create: `.github/scripts/modrinth.sh`
- Modify: `.github/workflows/build.yml`, `.github/workflows/codeql.yml`, `.github/dependabot.yml`, `CLAUDE.md`

**Interfaces:**
- Consumes: jar names `steamed-<loader>-<version>+<mc>.jar`; TOML `mod.version` and `["<mc>"].mod.mc_releases`
- Produces: `.github/scripts/modrinth.sh <jar-dir> <toml> <changelog-file>`, env `MODRINTH_TOKEN`, `DRY_RUN=1` prints payloads instead of uploading

- [ ] **Step 1: Write `.github/scripts/modrinth.sh`**

```bash
#!/usr/bin/env bash
# One Modrinth version per jar, named like 0.2.0+26.2-fabric. DRY_RUN=1 prints the payloads instead.
set -euo pipefail
jars="$1" toml="$2" changelog="$(cat "$3")"
fabric_deps='[{"project_id":"P7dR8mSH","dependency_type":"required"},{"project_id":"Ha28R6CL","dependency_type":"required"}]'
neoforge_deps='[{"project_id":"ordsPcFz","dependency_type":"required"}]'

for path in "$jars"/steamed-*.jar; do
  jar="$(basename "$path")"                 # steamed-fabric-0.2.0+26.2.jar
  rest="${jar#steamed-}"; loader="${rest%%-*}"
  rest="${rest#"$loader"-}"; rest="${rest%.jar}"
  version="${rest%%+*}"; mc="${rest#*+}"
  game_versions="$(python3 -c 'import json, sys, tomllib
print(json.dumps(tomllib.load(open(sys.argv[1], "rb"))[sys.argv[2]]["mod"]["mc_releases"]))' "$toml" "$mc")"
  deps="$fabric_deps"; [ "$loader" = neoforge ] && deps="$neoforge_deps"
  data="$(jq -n --arg v "$version+$mc-$loader" --arg l "$loader" --argjson gv "$game_versions" --arg c "$changelog" \
    --argjson deps "$deps" '{
      project_id: "SZuvIr1Y", name: "Steamed \($v)", version_number: $v, changelog: $c,
      version_type: "beta", loaders: [$l], game_versions: $gv, featured: false,
      dependencies: $deps, file_parts: ["file"], primary_file: "file"}')"
  if [ "${DRY_RUN:-}" = 1 ]; then echo "$jar -> $(jq -c 'del(.changelog)' <<<"$data")"; continue; fi
  curl --fail-with-body -sS -X POST https://api.modrinth.com/v2/version \
    -H "Authorization: $MODRINTH_TOKEN" -H "User-Agent: 0x00ACAB/steamed (GitHub Actions)" \
    --form-string "data=$data" -F "file=@$path;type=application/java-archive;filename=$jar"
  echo
done
```

Run: `chmod +x .github/scripts/modrinth.sh`

- [ ] **Step 2: Dry-run it against the built jars**

```bash
mkdir -p "$SCRATCH/dist" && cp versions/*/build/libs/steamed-*.jar "$SCRATCH/dist/"
echo notes > "$SCRATCH/notes.md"
DRY_RUN=1 .github/scripts/modrinth.sh "$SCRATCH/dist" stonecutter.properties.toml "$SCRATCH/notes.md"
```

Expected, one line each:
`steamed-fabric-0.1.4+26.2.jar -> {..."version_number":"0.1.4+26.2-fabric"...,"loaders":["fabric"],"game_versions":["26.2"],"dependencies":[{"project_id":"P7dR8mSH"...},{"project_id":"Ha28R6CL"...}]...}`, the same for `neoforge` (dependency `ordsPcFz` only) and `quilt` (Fabric deps).

- [ ] **Step 3: Rewrite `.github/workflows/build.yml`'s build job steps after `setup-gradle`**

```yaml
      - name: Check the tag matches mod.version
        if: startsWith(github.ref, 'refs/tags/v')
        run: |
          version="$(python3 -c 'import tomllib; print(tomllib.load(open("stonecutter.properties.toml", "rb"))["mod"]["version"])')"
          if [ "v$version" != "$GITHUB_REF_NAME" ]; then
            echo "::error::Tag $GITHUB_REF_NAME doesn't match mod.version $version"
            exit 1
          fi

      - name: Build
        run: ./gradlew build

      - name: Collect jars
        run: mkdir dist && cp versions/*/build/libs/steamed-*.jar dist/

      - name: Upload jars
        uses: actions/upload-artifact@v7
        with:
          name: steamed-jars
          path: dist/*.jar
          if-no-files-found: error
```

In the release job, update the comment to "publishes a pre-release with every node's jar", and replace the whole
"Publish to Modrinth" step's `run:` with:

```yaml
        run: |
          gh release view "$GITHUB_REF_NAME" --json body -q .body > changelog.md
          src/.github/scripts/modrinth.sh jars src/stonecutter.properties.toml changelog.md
```

- [ ] **Step 4: CodeQL compiles one node**

In `.github/workflows/codeql.yml` change `./gradlew classes --no-daemon --max-workers=2` to
`./gradlew :26.2-fabric:classes --no-daemon --max-workers=2`, and the comment above it to say only the
`26.2-fabric` node (and later `1.21.1-fabric`) is compiled, to stay inside the heap.

- [ ] **Step 5: Dependabot**

In `.github/dependabot.yml`, remove the `- /build-logic` line, and change the comment "in gradle.properties" to
"in stonecutter.properties.toml".

- [ ] **Step 6: Validate the YAML**

Run: `python3 -c 'import yaml,sys; [yaml.safe_load(open(f)) for f in sys.argv[1:]]' .github/workflows/*.yml .github/dependabot.yml && echo ok`
Expected: `ok`

- [ ] **Step 7: Rewrite `CLAUDE.md` for the new layout**

Update these sections (keep everything else):
- Stack: replace the multi-loader bullet with "Stonecutter 0.9.8 nodes (`<mc>-<loader>`) over one `src/` tree, built
  from Multicutter's layout (`pajicadvance/multicutter` at `bf13a3b`); Fabric through loom-back-compat"; versions and
  mod metadata live in `stonecutter.properties.toml` (UTF-8, so no `\u` escapes), expanded into `fabric.mod.json`,
  `neoforge.mods.toml`, `steamed.mixins.json` and the `IPlatformHelper` service file by each `build.<loader>.gradle.kts`
- Commands: `./gradlew build` builds and tests every node; `./gradlew :26.2-fabric:build` one node;
  `./gradlew :26.2-fabric:runClient` etc., run dirs under `run/<node>/`; jars in `versions/<node>/build/libs/`;
  the checked-in active node is `26.2-fabric`, switch back before committing
- CI: the tag check reads `mod.version` from the TOML; `.github/scripts/modrinth.sh` uploads one version per jar
  (`DRY_RUN=1` to test); CodeQL compiles single nodes
- Layout: replace the four-module description with `src/main/{kotlin,java,resources}`, `src/test`, loader code in
  `xyz.kkaii.steamed.{fabric,neoforge,quilt}` excluded per node by each build script's `sourceSets.main` block, and
  the `build.*.gradle.kts`/`stonecutter.*` files. Drop `build-logic` and the `commonJava` mechanism everywhere
  they're mentioned (including the Gotchas bullet about MultiLoader-Template)
- Gotchas: `:26.2-quilt:runClient` workarounds now live in `build.quilt.gradle.kts`

- [ ] **Step 8: Commit**

```bash
grep -q 'stonecutter active "26.2-fabric"' stonecutter.gradle.kts
git add -A && git commit -S -m "ci, docs: build and publish Stonecutter nodes

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 5: Restructure gate on real installs

**Files:** none (verification only)

- [ ] **Step 1: Install the new jars into the three Prism instances**

```bash
P=~/.local/share/PrismLauncher/instances
for pair in "Steamed Fabric dev:26.2-fabric" "Steamed NeoForge 0.1.3:26.2-neoforge" "Steamed Quilt 0.1.3:26.2-quilt"; do
  inst="${pair%%:*}" node="${pair##*:}"
  mkdir -p "$SCRATCH/prism-backup/$inst" && mv "$P/$inst/minecraft/mods/"steamed-*.jar "$SCRATCH/prism-backup/$inst/"
  cp versions/$node/build/libs/steamed-*.jar "$P/$inst/minecraft/mods/"
done
```

- [ ] **Step 2: Launch each and check the log** (Steam client running)

For each instance: `prismlauncher --launch "<instance>" --profile kaiii_uwu &`, wait ~90 s, then

```bash
grep -E "Starting steam poller task|Exception|Mixin.*(error|fail)" "$P/<instance>/minecraft/logs/latest.log" | head
```

Expected: `Starting steam poller task` and no exception or mixin failure lines. Close the game.

- [ ] **Step 3: Ask the user to eyeball the title screens** (no GUI automation here): mug icon next to Multiplayer,
  "Logged in as" line bottom right, on all three. Phase A is done when they confirm.

---

## Phase B: 1.21.1 port

### Task 6: Move 26.x-only API calls behind `compat` (26.2 only, no behaviour change)

**Files:**
- Create: `src/main/kotlin/xyz/kkaii/steamed/compat/Compat26.kt`
- Modify: `Steamed.kt`, `SteamInit.kt`, `SteamPlayers.kt`, `screen/SteamLobbyScreen.kt`, `screen/SteamFriendListScreen.kt`, `screen/SteamConnectingScreen.kt` (all under `src/main/kotlin/xyz/kkaii/steamed/`), `src/main/java/xyz/kkaii/steamed/mixin/TitleScreenMixin.java`, `PauseMenuMixin.java`

**Interfaces:**
- Produces (package `xyz.kkaii.steamed.compat`, Java facade class `Compat`), identical in Task 8's 1.21.1 file:
  - `typealias Graphics`
  - `fun Minecraft.currentScreen(): Screen?`, `fun Minecraft.show(screen: Screen)`, `fun Minecraft.closeTo(screen: Screen?)`
  - `fun Minecraft.systemMessage(message: Component)`, `fun Minecraft.toast(id: SystemToast.SystemToastId, title: Component, message: Component?)`
  - `fun Minecraft.disconnectTo(screen: Screen)`, `fun Minecraft.leaveWorld(reason: Component)`, `fun Minecraft.appendTitle(suffix: String)`
  - `fun Screen.pressKey(key: Int): Boolean`, `fun AbstractWidget.draw(graphics: Graphics, mouseX: Int, mouseY: Int, a: Float)`
  - `fun Graphics.face(uuid: UUID, x: Int, y: Int, size: Int)`
  - `fun minecraftVersionName(): String`, `fun argb(a: Int, r: Int, g: Int, b: Int): Int`, `fun fontStyle(font: Identifier): Style`, `fun Style.opensUrl(url: String, hover: Component): Style`
  - `fun publishForSteam(server: IntegratedServer, port: Int): Boolean`, `fun <T : Any> cycleButton(name: (T) -> Component, initial: T): CycleButton.Builder<T>`
  - `abstract class SteamedScreen(title: Component) : Screen` with `protected open fun draw(graphics: Graphics, mouseX: Int, mouseY: Int, a: Float)` and `protected open fun onKey(key: Int)`
  - `abstract class ContainerRow<E : ContainerRow<E>> : ContainerObjectSelectionList.Entry<E>` with `abstract fun draw(graphics: Graphics, x: Int, y: Int, width: Int, height: Int, mouseX: Int, mouseY: Int, a: Float)`
  - `abstract class SelectionRow<E : SelectionRow<E>> : ObjectSelectionList.Entry<E>` with the same `draw` and `protected open fun onClick()`

- [ ] **Step 1: Write `compat/Compat26.kt`**

```kotlin
//? if >=26.1 {
@file:JvmName("Compat")

package xyz.kkaii.steamed.compat

import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.server.IntegratedServer
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.util.ARGB
import net.minecraft.world.item.component.ResolvableProfile
import org.lwjgl.glfw.GLFW
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

typealias Graphics = GuiGraphicsExtractor

fun Minecraft.currentScreen(): Screen? = gui.screen()
fun Minecraft.show(screen: Screen) = setScreenAndShow(screen)
fun Minecraft.closeTo(screen: Screen?) = gui.setScreen(screen)
fun Minecraft.systemMessage(message: Component) = gui.hud.chat.addClientSystemMessage(message)
fun Minecraft.toast(id: SystemToast.SystemToastId, title: Component, message: Component?) =
    SystemToast.addOrUpdate(gui.toastManager(), id, title, message)
fun Minecraft.disconnectTo(screen: Screen) = disconnect(screen, false)
fun Minecraft.leaveWorld(reason: Component) = disconnectFromWorld(reason)
fun Minecraft.appendTitle(suffix: String) = window.setTitle(GLFW.glfwGetWindowTitle(window.handle()) + suffix)

fun Screen.pressKey(key: Int): Boolean = keyPressed(KeyEvent(key, 0, 0))
fun AbstractWidget.draw(graphics: Graphics, mouseX: Int, mouseY: Int, a: Float) =
    extractRenderState(graphics, mouseX, mouseY, a)

private val profiles = ConcurrentHashMap<UUID, ResolvableProfile>()
fun Graphics.face(uuid: UUID, x: Int, y: Int, size: Int) =
    PlayerFaceExtractor.extractRenderState(this, profiles.computeIfAbsent(uuid, ResolvableProfile::createUnresolved), x, y, size)

fun minecraftVersionName(): String = SharedConstants.getCurrentVersion().name()
fun argb(a: Int, r: Int, g: Int, b: Int): Int = ARGB.color(a, r, g, b)
fun fontStyle(font: Identifier): Style = Style.EMPTY.withFont(FontDescription.Resource(font))
fun Style.opensUrl(url: String, hover: Component): Style =
    withClickEvent(ClickEvent.OpenUrl(URI(url))).withHoverEvent(HoverEvent.ShowText(hover))

fun publishForSteam(server: IntegratedServer, port: Int): Boolean =
    server.publishServer(MinecraftServer.MultiplayerScope.LAN, server.defaultGameType, true, port)

fun <T : Any> cycleButton(name: (T) -> Component, initial: T): CycleButton.Builder<T> = CycleButton.builder(name, initial)

abstract class SteamedScreen(title: Component) : Screen(title) {
    protected open fun draw(graphics: Graphics, mouseX: Int, mouseY: Int, a: Float) {}
    protected open fun onKey(key: Int) {}

    override fun extractRenderState(graphics: Graphics, mouseX: Int, mouseY: Int, a: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, a)
        draw(graphics, mouseX, mouseY, a)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        onKey(event.key())
        return super.keyPressed(event)
    }
}

abstract class ContainerRow<E : ContainerRow<E>> : ContainerObjectSelectionList.Entry<E>() {
    abstract fun draw(graphics: Graphics, x: Int, y: Int, width: Int, height: Int, mouseX: Int, mouseY: Int, a: Float)

    override fun extractContent(graphics: Graphics, mouseX: Int, mouseY: Int, hovered: Boolean, a: Float) =
        draw(graphics, contentX, contentY, contentWidth, contentHeight, mouseX, mouseY, a)
}

abstract class SelectionRow<E : SelectionRow<E>> : ObjectSelectionList.Entry<E>() {
    abstract fun draw(graphics: Graphics, x: Int, y: Int, width: Int, height: Int, mouseX: Int, mouseY: Int, a: Float)
    protected open fun onClick() {}

    override fun extractContent(graphics: Graphics, mouseX: Int, mouseY: Int, hovered: Boolean, a: Float) =
        draw(graphics, contentX, contentY, contentWidth, contentHeight, mouseX, mouseY, a)

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        onClick()
        return super.mouseClicked(event, doubleClick)
    }
}
//?}
```

- [ ] **Step 2: Rewrite call sites** (import the used names from `xyz.kkaii.steamed.compat.*`; drop imports that become unused)

| 26.2 code | becomes |
|---|---|
| `X.gui.hud.chat.addClientSystemMessage(m)` | `X.systemMessage(m)` |
| `X.gui.screen()` | `X.currentScreen()` |
| `X.setScreenAndShow(s)` | `X.show(s)` |
| `X.gui.setScreen(s)` | `X.closeTo(s)` |
| `SystemToast.addOrUpdate(X.gui.toastManager(), ID, t, m)` | `X.toast(ID, t, m)` |
| `X.disconnect(screen, false)` | `X.disconnectTo(screen)` |
| `X.disconnectFromWorld(r)` | `X.leaveWorld(r)` |
| `instance.window.setTitle(GLFW.glfwGetWindowTitle(instance.window.handle()) + " in a steam lobby")` | `instance.appendTitle(" in a steam lobby")` |
| `screen?.keyPressed(KeyEvent(KEY_F5, 0, 0))` | `screen?.pressKey(KEY_F5)` |
| `SharedConstants.getCurrentVersion().name()` | `minecraftVersionName()` |
| `ARGB.color(` | `argb(` |
| `Style.EMPTY.withFont(FontDescription.Resource(id))` | `fontStyle(id)` |
| `.withClickEvent(ClickEvent.OpenUrl(URI(url))).withHoverEvent(HoverEvent.ShowText(h))` | `.opensUrl(url, h)` |
| `server.publishServer(MinecraftServer.MultiplayerScope.LAN, server.defaultGameType, true, port)` | `publishForSteam(server, port)` |
| `CycleButton.builder<T>(name, initial)` | `cycleButton<T>(name, initial)` |
| widget `it.extractRenderState(graphics, mouseX, mouseY, a)` / `button.extractRenderState(...)` | `it.draw(graphics, mouseX, mouseY, a)` |
| `GuiGraphicsExtractor` | `Graphics` |
| `PlayerFaceExtractor.extractRenderState(graphics, profile, x, y, 24)` | `graphics.face(uuid, x, y, 24)` |

Files and the structural changes beyond the table:
- `Steamed.kt`, `SteamInit.kt`: table only. `SteamInit.kt` drops the `KeyEvent` and `GLFW` imports
- `SteamPlayers.kt`: `ICON` becomes `Component.literal("").withStyle(fontStyle(Identifier.fromNamespaceAndPath("steamed", "nametag")))`;
  delete `fun head(uuid: UUID)` and the `PlayerSprite`, `ResolvableProfile`, `FontDescription` imports
- `SteamConnectingScreen`: extends `SteamedScreen`; `override fun extractRenderState(...)` becomes
  `override fun draw(graphics: Graphics, mouseX: Int, mouseY: Int, a: Float)` without the `super` call (the base
  class calls super first)
- `SteamLobbyScreen`: extends `SteamedScreen`; `keyPressed(event: KeyEvent)` becomes

```kotlin
    override fun onKey(key: Int) {
        if (key == KEY_F5) this.screenExecutor.execute { this.minecraft.show(SteamLobbyScreen(this.lastScreen)) }
    }
```

  `MemberList.Entry` extends `ContainerRow<Entry>`, `HostList.Row` extends `ContainerRow<Row>`. Each
  `extractContent(graphics, mouseX, mouseY, hovered, a)` becomes `draw(graphics, x, y, width, height, mouseX, mouseY, a)`
  and uses `x`/`y`/`width`/`height`/`x + width`/`y + height` where it used `contentX`/`contentY`/`contentWidth`/
  `contentHeight`/`contentRight`/`contentBottom`. `Row.drawRow` and `Row.placeButtons` take `x, y, width, height`
  parameters the same way. `MemberList.Entry.head` becomes `private var head: UUID?` set from
  `SteamPlayers.memberUuid(lobby, steamId)`; `drawRow` draws `face?.let { graphics.face(it, faceX, y + (height - 24) / 2, 24) }`
- `SteamFriendListScreen`: `Entry` extends `SelectionRow<Entry>`; `head: Component?` becomes `uuid: UUID?` from
  `SteamPlayers.friendUuid(steamId)`; `mouseClicked` becomes `override fun onClick() { if (!invited) this@FriendSelectionList.selected = this }`; the draw is

```kotlin
            override fun draw(graphics: Graphics, x: Int, y: Int, width: Int, height: Int, mouseX: Int, mouseY: Int, a: Float) {
                val font = this@SteamFriendListScreen.font
                val label = if (invited) "$name (invited)" else "$name - $state"
                val face = uuid
                val total = font.width(label) + if (face != null) 10 else 0
                var left = this@FriendSelectionList.width / 2 - total / 2
                val top = y + (height - 9) / 2
                if (face != null) {
                    graphics.face(face, left, top, 8)
                    left += 10
                }
                graphics.text(font, label, left, top, -1)
            }
```

- Mixins: `TitleScreenMixin` and `PauseMenuMixin` call `Compat.show(this.minecraft, new SteamLobbyScreen(Compat.currentScreen(this.minecraft)))` /
  `Compat.show(this.minecraft, new SteamLobbyScreen(this))` (import `xyz.kkaii.steamed.compat.Compat`), and every
  `_ ->` lambda parameter becomes `button ->` (Java 21 has no unnamed variables)

- [ ] **Step 3: Check nothing 26.x-only is left outside compat**

```bash
grep -rnE 'gui\.screen\(\)|setScreenAndShow|gui\.hud|toastManager|GuiGraphicsExtractor|extractRenderState|extractContent|KeyEvent|MouseButtonEvent|ARGB|FontDescription|PlayerFaceExtractor|ResolvableProfile|PlayerSprite|ClickEvent|HoverEvent|MultiplayerScope|disconnectFromWorld|glfwGetWindowTitle|gui\.setScreen' \
  src/main/kotlin --exclude-dir=compat
grep -rn '_ ->' src/main/java
```

Expected: no output from either.

- [ ] **Step 4: Build all 26.2 nodes**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`, tests pass.

- [ ] **Step 5: Smoke test in a dev client**

`./gradlew :26.2-fabric:runClient --args='--quickPlaySingleplayer "New World"'` in the background; wait for
`Starting steam poller task` and the world to load; check
`grep -E "Exception|Mixin.*(error|fail)" run/26.2-fabric/logs/latest.log` prints nothing; stop it. Then ask the user
to open the lobby screen, create a lobby, open the invite screen (faces still drawn beside names) and press F5.

- [ ] **Step 6: Commit**

```bash
grep -q 'stonecutter active "26.2-fabric"' stonecutter.gradle.kts
git add -A && git commit -S -m "refactor: route version-specific Minecraft calls through compat

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 7: Add the 1.21.1 nodes and verify the API list against decompiled sources

**Files:**
- Modify: `settings.gradle.kts`, `stonecutter.gradle.kts`, `stonecutter.properties.toml`, `.github/workflows/codeql.yml`
- Create: `docs/superpowers/notes/2026-10-17-1.21.1-api-check.md`

**Interfaces:**
- Produces: nodes `1.21.1-fabric`, `1.21.1-neoforge`; TOML section `["1.21.1"]`; Stonecutter replacements
  `Identifier`↔`ResourceLocation` and `import net.minecraft.util.Util`↔`import net.minecraft.Util`; the checked API
  table that Tasks 8 and 9 follow

- [ ] **Step 1: Nodes**

`settings.gradle.kts`, inside `create(rootProject)`:

```kotlin
        match("1.21.1", "fabric", "neoforge")
        match("26.2", "fabric", "neoforge", "quilt")
```

- [ ] **Step 2: Replacements in `stonecutter.gradle.kts`** (source is written in 26.x names; Stonecutter rewrites them for older nodes)

```kotlin
stonecutter parameters {
    val (_, loader) = current.project.split('-', limit = 2)
    properties { tags(current.version, loader) }
    constants { match(loader, "fabric", "neoforge", "quilt") }
    replacements {
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
            replace("import net.minecraft.Util", "import net.minecraft.util.Util")
        }
    }
}
```

- [ ] **Step 3: TOML section** (append to `stonecutter.properties.toml`; the mixin list is final for Task 9)

```toml
["1.21.1"]
mod.java = "21"
mod.mc_releases = ["1.21", "1.21.1"]
mod.mixins = ["ChatListenerMixin", "ConnectScreenAccessor", "IntegratedServerMixin", "MinecraftAccessor",
    "PauseMenuMixin", "PlayerListMixin", "PlayerRendererMixin", "PlayerTabOverlayMixin", "SplashManagerMixin",
    "TitleScreenMixin"]
deps.fabric_api = "0.116.17+1.21.1"
deps.neo = "21.1.262"
meta.minecraft_fabric = ">=1.21 <=1.21.1"
meta.minecraft_neoforge = "[1.21,1.21.2)"
meta.neo = "[21.1,)"
meta.kff = "[5.12,)"
```

- [ ] **Step 4: Generate sources**

```bash
./gradlew :26.2-fabric:genSourcesWithVineflower :1.21.1-fabric:genSourcesWithVineflower
find ~/.gradle/caches versions/1.21.1-fabric/.gradle .gradle -name '*1.21.1*sources*.jar' 2>/dev/null | head
find ~/.gradle/caches versions/26.2-fabric/.gradle .gradle -name '*26.2*sources*.jar' 2>/dev/null | head
mkdir -p "$SCRATCH/src-1.21.1" "$SCRATCH/src-26.2"
# unzip the client (merged) sources jar of each into those dirs
```

Expected: one Minecraft sources jar per version; unzipped trees contain `net/minecraft/client/Minecraft.java`.
Also confirm `:1.21.1-neoforge` configures: `./gradlew :1.21.1-neoforge:dependencies --configuration compileClasspath -q | head`.

- [ ] **Step 5: Check every expected difference and write the notes file**

For each row, open the named class in `$SCRATCH/src-1.21.1` (and `$SCRATCH/src-26.2` where the 26.2 side is in
question) and record the real signature. Rows whose real signature differs from the "expected" column are fixed in
Task 8/9's code before writing it; note the change in the file.

`docs/superpowers/notes/2026-10-17-1.21.1-api-check.md`:

```markdown
# 1.21.1 API check (against Vineflower sources, <date>)

| Use | Expected in 1.21.1 | Found |
|---|---|---|
| current screen | `Minecraft.screen` field, `setScreen(Screen?)` | |
| chat line | `gui.getChat().addMessage(Component)` | |
| toasts | `Minecraft.getToasts()`; `SystemToast.addOrUpdate(ToastComponent, SystemToastId, Component, Component?)`; `SystemToastId(long)` | |
| leave a server | `ClientLevel.disconnect()` then `Minecraft.disconnect(Screen)`; what does 26.2 `Minecraft.disconnectFromWorld(Component)` show? | |
| window title | `Minecraft.createTitle()` private; `GLFW.glfwGetWindowTitle` absent in LWJGL 3.3.3 | |
| version name | `SharedConstants.getCurrentVersion().getName()` | |
| colours | `FastColor.ARGB32.color(a, r, g, b)` | |
| font style | `Style.withFont(ResourceLocation)` | |
| click/hover | `new ClickEvent(Action.OPEN_URL, String)`, `new HoverEvent(Action.SHOW_TEXT, Component)` | |
| publish | `IntegratedServer.publishServer(GameType, boolean, int)`; inside it `startTcpServerListener(InetAddress, int)` and `LanServerPinger.start()` | |
| player cap | `IntegratedServer` has no `getMaxPlayers` override; `PlayerList.maxPlayers` field read in `canPlayerLogin`; `getMaxPlayers()` getter | |
| cycle button | `CycleButton.builder(Function)` + `.withInitialValue(T)` | |
| screen render | `Screen.render(GuiGraphics, int, int, float)`; `keyPressed(int, int, int)`; `screenExecutor` exists | |
| list entries | `ContainerObjectSelectionList.Entry.render(GuiGraphics, index, top, left, width, height, mouseX, mouseY, hovered, partialTick)`; `ObjectSelectionList.Entry.mouseClicked(double, double, int)` and what vanilla entries return | |
| list helpers | `AbstractSelectionList(Minecraft, int, int, int, int)`, `updateSize(int, HeaderAndFooterLayout)`, `replaceEntries` | |
| widgets | `MultiLineTextWidget.setCentered`, `ImageButton(int,int,int,int,WidgetSprites,OnPress,Component)`, `AbstractWidget.render` | |
| text | `GuiGraphics.drawString(Font, Component/String, int, int, int)`, `drawCenteredString(Font, Component, int, int, int)` | |
| faces | `PlayerFaceRenderer.draw(GuiGraphics, PlayerSkin, int, int, int)`; `SkullBlockEntity.fetchGameProfile(UUID)`; `SkinManager.getOrLoad(GameProfile): CompletableFuture<PlayerSkin>`; `DefaultPlayerSkin.get(UUID)` | |
| nametag | `PlayerRenderer.renderNameTag(AbstractClientPlayer, Component, PoseStack, MultiBufferSource, int, float)` | |
| tab list | `PlayerTabOverlay.getNameForDisplay(PlayerInfo)`; `GameProfile.getId()` | |
| chat listener | `ChatListener.handleSystemMessage(Component, boolean)` | |
| splashes | `SplashManager.splashes` is `final List<String>`; `apply(List, ResourceManager, ProfilerFiller)`; `getSplash()`; `SplashRenderer(String)`, `CHRISTMAS`/`NEW_YEAR`/`HALLOWEEN` | |
| pause menu | `PauseScreen.createPauseMenu()` has exactly one `GridLayout.RowHelper` local, then `GridLayout.arrangeElements()` | |
| connect | `ConnectScreen.updateStatus(Component)`; `ConnectScreen.startConnecting(Screen, Minecraft, ServerAddress, ServerData, boolean, TransferState)` | |
| other | `Util` in `net.minecraft`; `User.getProfileId()`; `Window.setTitle`; `LoadingDotsText.get(long)` | |
```

Fill every "Found" cell. For the "leave a server" row, write down the exact screen 26.2's `disconnectFromWorld`
ends on; Task 8's `leaveWorld` must end on the same kind of screen.

- [ ] **Step 6: CodeQL compiles the 1.21.1 Fabric node too** (from Task 10 on it compiles)

In `.github/workflows/codeql.yml`: `./gradlew :26.2-fabric:classes :1.21.1-fabric:classes --no-daemon --max-workers=2`.

- [ ] **Step 7: Confirm 26.2 still builds, then commit** (1.21.1 nodes don't compile yet; that's Tasks 8-9)

```bash
./gradlew :26.2-fabric:build :26.2-neoforge:build :26.2-quilt:build
grep -q 'stonecutter active "26.2-fabric"' stonecutter.gradle.kts
git add -A && git commit -S -m "build: 1.21.1-fabric and 1.21.1-neoforge nodes, API check

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 8: 1.21.1 compat file (Kotlin compiles on 1.21.1)

**Files:**
- Create: `src/main/kotlin/xyz/kkaii/steamed/compat/Compat1_21_1.kt`
- Create: `src/main/java/xyz/kkaii/steamed/mixin/MinecraftAccessor.java` (1.21.1 only, needed by `appendTitle`)

**Interfaces:**
- Consumes: every signature listed under Task 6 "Produces"; the API notes from Task 7
- Produces: the same names for 1.21.1, plus `GuiGraphics.text(...)`/`centeredText(...)` extensions (26.x has them as members)

- [ ] **Step 1: Switch the active node to 1.21.1-fabric** so the 1.21.1 branches are uncommented in `src/`

Run: `./gradlew tasks --group stonecutter` and run the "set active project to 1.21.1-fabric" task it lists.
Expected: `Compat26.kt` is now fully commented out.

- [ ] **Step 2: Write `compat/Compat1_21_1.kt`** (shown here in its 1.21.1-active form; Stonecutter comments it out for 26.x. Adjust any line Task 7's notes contradict)

```kotlin
//? if <26.1 {
@file:JvmName("Compat")

package xyz.kkaii.steamed.compat

import com.mojang.authlib.GameProfile
import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.screens.DisconnectedScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.client.resources.PlayerSkin
import net.minecraft.client.server.IntegratedServer
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.util.FastColor
import net.minecraft.world.level.block.entity.SkullBlockEntity
import xyz.kkaii.steamed.mixin.MinecraftAccessor
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

typealias Graphics = GuiGraphics

fun Minecraft.currentScreen(): Screen? = screen
fun Minecraft.show(screen: Screen) = setScreen(screen)
fun Minecraft.closeTo(screen: Screen?) = setScreen(screen)
fun Minecraft.systemMessage(message: Component) = gui.chat.addMessage(message)
fun Minecraft.toast(id: SystemToast.SystemToastId, title: Component, message: Component?) =
    SystemToast.addOrUpdate(toasts, id, title, message)
fun Minecraft.disconnectTo(screen: Screen) {
    level?.disconnect()
    disconnect(screen)
}
// Mirrors 26.x disconnectFromWorld (see the API notes).
fun Minecraft.leaveWorld(reason: Component) =
    disconnectTo(DisconnectedScreen(TitleScreen(), Component.translatable("steamed.disconnected"), reason))
fun Minecraft.appendTitle(suffix: String) = window.setTitle((this as MinecraftAccessor).`steamed$createTitle`() + suffix)

fun Screen.pressKey(key: Int): Boolean = keyPressed(key, 0, 0)
fun AbstractWidget.draw(graphics: Graphics, mouseX: Int, mouseY: Int, a: Float) = render(graphics, mouseX, mouseY, a)

fun GuiGraphics.text(font: Font, text: Component, x: Int, y: Int, color: Int) {
    drawString(font, text, x, y, color)
}
fun GuiGraphics.text(font: Font, text: String, x: Int, y: Int, color: Int) {
    drawString(font, text, x, y, color)
}
fun GuiGraphics.centeredText(font: Font, text: Component, x: Int, y: Int, color: Int) =
    drawCenteredString(font, text, x, y, color)

// Fetched once per UUID; the default skin stands in until (or unless) the real one loads.
private val skins = ConcurrentHashMap<UUID, PlayerSkin>()
private val requested: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

private fun skin(uuid: UUID): PlayerSkin {
    skins[uuid]?.let { return it }
    if (requested.add(uuid)) {
        val skinManager = Minecraft.getInstance().skinManager
        SkullBlockEntity.fetchGameProfile(uuid)
            .thenCompose { profile: java.util.Optional<GameProfile> ->
                if (profile.isPresent) skinManager.getOrLoad(profile.get())
                else CompletableFuture.completedFuture<PlayerSkin?>(null)
            }
            .thenAccept { skin -> if (skin != null) skins[uuid] = skin }
    }
    return DefaultPlayerSkin.get(uuid)
}

fun Graphics.face(uuid: UUID, x: Int, y: Int, size: Int) = PlayerFaceRenderer.draw(this, skin(uuid), x, y, size)

fun minecraftVersionName(): String = SharedConstants.getCurrentVersion().name
fun argb(a: Int, r: Int, g: Int, b: Int): Int = FastColor.ARGB32.color(a, r, g, b)
fun fontStyle(font: Identifier): Style = Style.EMPTY.withFont(font)
fun Style.opensUrl(url: String, hover: Component): Style =
    withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, url)).withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))

fun publishForSteam(server: IntegratedServer, port: Int): Boolean =
    server.publishServer(server.defaultGameType, true, port)

fun <T : Any> cycleButton(name: (T) -> Component, initial: T): CycleButton.Builder<T> =
    CycleButton.builder(name).withInitialValue(initial)

abstract class SteamedScreen(title: Component) : Screen(title) {
    protected open fun draw(graphics: Graphics, mouseX: Int, mouseY: Int, a: Float) {}
    protected open fun onKey(key: Int) {}

    override fun render(graphics: Graphics, mouseX: Int, mouseY: Int, a: Float) {
        super.render(graphics, mouseX, mouseY, a)
        draw(graphics, mouseX, mouseY, a)
    }

    override fun keyPressed(key: Int, scanCode: Int, modifiers: Int): Boolean {
        onKey(key)
        return super.keyPressed(key, scanCode, modifiers)
    }
}

abstract class ContainerRow<E : ContainerRow<E>> : ContainerObjectSelectionList.Entry<E>() {
    abstract fun draw(graphics: Graphics, x: Int, y: Int, width: Int, height: Int, mouseX: Int, mouseY: Int, a: Float)

    override fun render(
        graphics: Graphics, index: Int, top: Int, left: Int, width: Int, height: Int,
        mouseX: Int, mouseY: Int, hovered: Boolean, a: Float,
    ) = draw(graphics, left, top, width, height, mouseX, mouseY, a)
}

abstract class SelectionRow<E : SelectionRow<E>> : ObjectSelectionList.Entry<E>() {
    abstract fun draw(graphics: Graphics, x: Int, y: Int, width: Int, height: Int, mouseX: Int, mouseY: Int, a: Float)
    protected open fun onClick() {}

    override fun render(
        graphics: Graphics, index: Int, top: Int, left: Int, width: Int, height: Int,
        mouseX: Int, mouseY: Int, hovered: Boolean, a: Float,
    ) = draw(graphics, left, top, width, height, mouseX, mouseY, a)

    // 1.21.1's list only focuses an entry that reports the click as handled.
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        onClick()
        return true
    }
}
//?}
```

- [ ] **Step 3: Write `mixin/MinecraftAccessor.java`** (1.21.1-active form)

```java
//? if <26.1 {
package xyz.kkaii.steamed.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
	@Invoker("createTitle")
	String steamed$createTitle();
}
//?}
```

- [ ] **Step 4: Compile Kotlin for both 1.21.1 nodes**

Run: `./gradlew :1.21.1-fabric:compileKotlin :1.21.1-neoforge:compileKotlin`
Expected: `BUILD SUCCESSFUL`. Each error points at a signature the notes got wrong: fix it in
`Compat1_21_1.kt` (never in shared code with an inline condition) and update the notes row.

- [ ] **Step 5: Switch back, confirm 26.2 builds, commit**

```bash
# run the "set active project to 26.2-fabric" task
grep -q 'stonecutter active "26.2-fabric"' stonecutter.gradle.kts
./gradlew :26.2-fabric:build :26.2-neoforge:build :26.2-quilt:build :1.21.1-fabric:compileKotlin :1.21.1-neoforge:compileKotlin
git add -A && git commit -S -m "feat: 1.21.1 compat layer

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 9: 1.21.1 mixins (every node builds)

**Files:**
- Modify (inline `//?` blocks): `src/main/java/xyz/kkaii/steamed/mixin/IntegratedServerMixin.java`, `PauseMenuMixin.java`, `PlayerTabOverlayMixin.java`, `SplashManagerMixin.java`
- Modify (whole-file `//? if >=26.1 {`): `AvatarRendererMixin.java`
- Create (whole-file `//? if <26.1 {`): `PlayerRendererMixin.java`, `PlayerListMixin.java`

**Interfaces:**
- Consumes: `SteamPlayers.decorate(UUID, Component)`, `Steamed.publishingForSteam`, `Steamed.hostedMaxPlayers` (Java statics), `Splashes.extraSplashes(): List<Component>`, `Splashes.personalSplash(): Component?`, `Compat.show`/`Compat.currentScreen`

Do this task with `1.21.1-fabric` active (as in Task 8 Step 1) so the code below reads as written; the 26.x branches
are then the commented ones.

- [ ] **Step 1: `AvatarRendererMixin.java`**: add `//? if >=26.1 {` as the first line and `//?}` as the last. With 1.21.1 active, Stonecutter turns the body into a comment

- [ ] **Step 2: `PlayerRendererMixin.java`**

```java
//? if <26.1 {
package xyz.kkaii.steamed.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import xyz.kkaii.steamed.SteamPlayers;

@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {
	@ModifyVariable(
			method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
			at = @At("HEAD"),
			argsOnly = true
	)
	private Component steamed$markSteamPlayer(Component name, @Local(argsOnly = true) AbstractClientPlayer player) {
		return SteamPlayers.decorate(player.getUUID(), name);
	}
}
//?}
```

- [ ] **Step 3: `PlayerListMixin.java`** (1.21.1's cap; `IntegratedPlayerList` passes 8 to the constructor)

```java
//? if <26.1 {
package xyz.kkaii.steamed.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.server.IntegratedPlayerList;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.kkaii.steamed.Steamed;

@Mixin(PlayerList.class)
public class PlayerListMixin {
	// Vanilla caps an integrated server at 8 players, below what the lobby may allow.
	@Inject(method = "getMaxPlayers", at = @At("RETURN"), cancellable = true)
	private void steamed$lobbyMaxPlayers(CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(steamed$cap(cir.getReturnValueI()));
	}

	@ModifyExpressionValue(
			method = "canPlayerLogin",
			at = @At(value = "FIELD", target = "Lnet/minecraft/server/players/PlayerList;maxPlayers:I")
	)
	private int steamed$lobbyLoginCap(int max) {
		return steamed$cap(max);
	}

	@Unique
	private int steamed$cap(int max) {
		return (Object) this instanceof IntegratedPlayerList && Steamed.hostedMaxPlayers > max ? Steamed.hostedMaxPlayers : max;
	}
}
//?}
```

Check against `$SCRATCH/src-1.21.1/net/minecraft/server/players/PlayerList.java`: `canPlayerLogin` must read the
`maxPlayers` field (that's what `defaultRequire: 1` will enforce at launch); if any other method in `PlayerList`
compares against `this.maxPlayers` for logins, add it to the `method` list.

- [ ] **Step 4: `IntegratedServerMixin.java`**: version the two `method =` lines and the cap

```java
	@ModifyArg(
			//? if >=26.1 {
			/*method = "publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;I)Z",
			*///?} else {
			method = "publishServer(Lnet/minecraft/world/level/GameType;ZI)Z",
			//?}
```

(the same block for the `@WrapOperation`), and wrap the whole `steamed$lobbyMaxPlayers` method plus its comment in
`//? if >=26.1 {` … `//?}`, since 1.21.1's cap lives in `PlayerListMixin`.

- [ ] **Step 5: `PauseMenuMixin.java`**: 1.21.1 has no local variable names, so match the local by type

```java
	//? if >=26.1 {
	/*private void createPauseMenu(CallbackInfo ci, @Local(name = "helper") GridLayout.RowHelper helper) {
	*///?} else {
	private void createPauseMenu(CallbackInfo ci, @Local GridLayout.RowHelper helper) {
	//?}
```

- [ ] **Step 6: `PlayerTabOverlayMixin.java`**

```java
		//? if >=26.1 {
		/*cir.setReturnValue(SteamPlayers.decorate(info.getProfile().id(), cir.getReturnValue()));
		*///?} else {
		cir.setReturnValue(SteamPlayers.decorate(info.getProfile().getId(), cir.getReturnValue()));
		//?}
```

- [ ] **Step 7: `SplashManagerMixin.java`**: 1.21.1 holds plain strings in a final list

Version the field and both method bodies:

```java
	//? if >=26.1 {
	/*@Shadow
	private List<Component> splashes;
	*///?} else {
	@Shadow
	@Final
	private List<String> splashes;
	//?}
```

In `steamed$addSplashes`, the parameter type `List<Component> preparations` becomes `List<?> preparations` in both
versions (same erasure), and the body:

```java
		//? if >=26.1 {
		/*List<Component> all = new ArrayList<>(this.splashes);
		all.addAll(Splashes.extraSplashes());
		this.splashes = List.copyOf(all);
		*///?} else {
		for (Component splash : Splashes.extraSplashes()) this.splashes.add(splash.getString());
		//?}
```

In `steamed$personalSplash`, the last line:

```java
		//? if >=26.1 {
		/*if (personal != null) cir.setReturnValue(new SplashRenderer(personal));
		*///?} else {
		if (personal != null) cir.setReturnValue(new SplashRenderer(personal.getString()));
		//?}
```

Add `import org.spongepowered.asm.mixin.Final;` (used only on 1.21.1; an unused import is harmless on 26.2).

- [ ] **Step 8: Build every node**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL` for all five nodes, tests pass on each. On Fabric 1.21.1, also check the refmap
was written: `unzip -l versions/1.21.1-fabric/build/libs/steamed-fabric-0.1.4+1.21.1.jar | grep refmap`.

- [ ] **Step 9: Switch back, rebuild, commit**

```bash
# run the "set active project to 26.2-fabric" task
grep -q 'stonecutter active "26.2-fabric"' stonecutter.gradle.kts
./gradlew build
git add -A && git commit -S -m "feat: 1.21.1 mixins

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 10: 1.21.1 jar checks and dev-client smoke tests

**Files:** none unless a check fails

- [ ] **Step 1: Metadata check across all five jars** (Review Focus 1 and 5)

```bash
for j in versions/*/build/libs/steamed-*.jar; do
  echo "== $j"
  unzip -p "$j" fabric.mod.json 2>/dev/null | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["version"], d["depends"])' 2>/dev/null
  unzip -p "$j" META-INF/neoforge.mods.toml 2>/dev/null | python3 -c 'import tomllib,sys; d=tomllib.loads(sys.stdin.read()); print(d["mods"][0]["version"], [(x["modId"], x["versionRange"]) for x in d["dependencies"]["steamed"]])' 2>/dev/null
  unzip -p "$j" steamed.mixins.json | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["compatibilityLevel"], d["mixins"])'
  unzip -p "$j" META-INF/services/xyz.kkaii.steamed.platform.IPlatformHelper
done
```

Expected: every version is exactly `0.1.4` (no `+`); 1.21.1 Fabric: `minecraft >=1.21 <=1.21.1`, `java >=21`,
`JAVA_21`, the ten 1.21.1 mixins; 1.21.1 NeoForge: `neoforge [21.1,)`, `minecraft [1.21,1.21.2)`,
`kotlinforforge [5.12,)`; each service file names its own loader's helper; no jar contains another loader's
classes (`unzip -l <jar> | grep -E 'steamed/(fabric|neoforge|quilt)/'` shows only its own).

- [ ] **Step 2: `:1.21.1-fabric:runClient`** (Steam running)

`./gradlew :1.21.1-fabric:runClient --args='--quickPlaySingleplayer "New World"'` in the background (the first run
creates `run/1.21.1-fabric`; without a world there the game stops at the title screen, which is fine). Wait for
`Starting steam poller task` and the title screen or world, then:

`grep -E "Exception|Mixin.*(error|fail)|InvalidInjection" run/1.21.1-fabric/logs/latest.log`
Expected: nothing. Stop the client.

- [ ] **Step 3: `:1.21.1-neoforge:runClient`**, same check on `run/1.21.1-neoforge/logs/latest.log`

- [ ] **Step 4: Commit any fixes** (same commit form as earlier tasks, message `fix: <what>`)

### Task 11: 1.21.1 gate on real installs

**Files:** none

- [ ] **Step 1: Ask the user to create two Prism instances**, since Prism instance creation isn't scriptable here:
  - `Steamed Fabric 1.21.1`: Minecraft 1.21.1, Fabric Loader 0.19.5, mods Fabric API 0.116.17+1.21.1 and FLK 1.14.1+kotlin.2.4.20
  - `Steamed NeoForge 1.21.1`: Minecraft 1.21.1, NeoForge 21.1.262, mod KFF 5.12.0

- [ ] **Step 2: Copy the jars in and launch headless**

```bash
cp versions/1.21.1-fabric/build/libs/steamed-*.jar "$P/Steamed Fabric 1.21.1/minecraft/mods/"
cp versions/1.21.1-neoforge/build/libs/steamed-*.jar "$P/Steamed NeoForge 1.21.1/minecraft/mods/"
```

Launch each with `prismlauncher --launch "<instance>" --profile kaiii_uwu`, wait ~90 s, check the log as in Task 5
Step 2. Expected: `Starting steam poller task`, no exceptions.

- [ ] **Step 3: User checklist, per 1.21.1 loader** (hand this list over verbatim)
  1. Title screen: mug icon next to Multiplayer renders; "Logged in as …" line bottom right
  2. Open a world, open the lobby from the pause menu, create a lobby with max players 16: the member list shows you with your face
  3. Run `/list`: "max of 16 players" (Review Focus 2)
  4. Open the invite screen: friends listed, faces appear within a few seconds or stay default without errors
  5. Leave the lobby, then vanilla "Open to LAN": the chat message appears and another machine on the LAN sees the world (or `ss -tlnp | grep java` shows the port bound on `*`/`0.0.0.0`, not `127.0.0.1`) (Review Focus 3)

- [ ] **Step 4: Record results** in `MILESTONES.md` under the backports item (dates, versions, what's verified,
  what's still blocked on a second Steam account: joining, auth, kick, voice, web maps, the nametag marker seen
  from another client, version-gate greying in a friend's list), and commit `docs: record 1.21.1 verification`.

### Task 12: Prepare 0.2.0

**Files:**
- Modify: `stonecutter.properties.toml` (`mod.version = "0.2.0"`), `README.md`, `MILESTONES.md`, `CLAUDE.md`
- Create: `.github/release-notes/v0.2.0.md`

- [ ] **Step 1: Bump the version**: `mod.version = "0.2.0"` in `stonecutter.properties.toml`

- [ ] **Step 2: Release notes** `.github/release-notes/v0.2.0.md`, in the style of `v0.1.4.md`: Minecraft 1.21.1
  support (Fabric and NeoForge, with required mods and versions per jar), one jar per Minecraft version and loader
  with the new names, 26.2 unchanged in behaviour, invite-screen heads now drawn beside names, which features are
  verified on 1.21.1 and which still need a two-account test, and that 1.21.1 and 26.2 players can't join each
  other (the lobby list says why)

- [ ] **Step 3: README**: add a 1.21.1 badge and rows to the requirements table (1.21.1 Fabric: Fabric API +
  FLK; 1.21.1 NeoForge: KFF 5.12+), and fix the NeoForge 26.2 row, which still says "nothing else (Kotlin is
  bundled)" although 0.1.4 requires Kotlin for Forge

- [ ] **Step 4: CLAUDE.md**: Stack lists both Minecraft versions and their loaders; the Gotchas about 26.x renames
  point at `compat/Compat26.kt`/`Compat1_21_1.kt` as the only place version differences go besides mixins; note
  that 1.21.1 is obfuscated (Fabric remaps through loom-back-compat, refmaps needed, no local variable names for
  `@Local(name = …)`) and that the 1.21.1 player cap is `PlayerListMixin`, not `IntegratedServerMixin`

- [ ] **Step 5: Full build and the Modrinth dry run**

```bash
./gradlew clean build
rm -rf "$SCRATCH/dist" && mkdir -p "$SCRATCH/dist" && cp versions/*/build/libs/steamed-*.jar "$SCRATCH/dist/"
ls "$SCRATCH/dist"
DRY_RUN=1 .github/scripts/modrinth.sh "$SCRATCH/dist" stonecutter.properties.toml .github/release-notes/v0.2.0.md
```

Expected: five jars `steamed-{fabric,neoforge}-0.2.0+1.21.1.jar`, `steamed-{fabric,neoforge,quilt}-0.2.0+26.2.jar`;
five payload lines, 1.21.1 ones with `"game_versions":["1.21","1.21.1"]` and versions like `0.2.0+1.21.1-fabric`.

- [ ] **Step 6: Commit, then stop and ask**

```bash
grep -q 'stonecutter active "26.2-fabric"' stonecutter.gradle.kts
git add -A && git commit -S -m "release 0.2.0

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

Then ask the user whether to merge `stonecutter-1.21.1` into `main`, push, and push tag `v0.2.0` (which publishes
the GitHub pre-release and five Modrinth versions). Do none of these without a yes.
