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
    replacements {
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
            replace("import net.minecraft.Util", "import net.minecraft.util.Util")
        }
    }
}
