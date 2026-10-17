package xyz.kkaii.steamed

import com.codedisaster.steamworks.SteamAPI
import com.codedisaster.steamworks.SteamLibraryLoader
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

// JNI binds a native library to the class loader of whoever calls System.load, so this has to run from a class that
// shares SteamAPI's loader. steamworks4j's LWJGL loader calls it from LWJGL's own class, which NeoForge keeps in a
// different loader from mods.
object SteamNatives : SteamLibraryLoader {
    private val LOGGER = LoggerFactory.getLogger("Steamed/Natives")

    override fun loadLibrary(libraryName: String): Boolean {
        val windows64 = System.getProperty("os.name").startsWith("Windows") &&
                System.getProperty("os.arch").contains("64")
        val fileName = System.mapLibraryName(if (windows64) "${libraryName}64" else libraryName)
        return try {
            System.load(extract(fileName).toString())
            true
        } catch (e: Throwable) {
            LOGGER.error("Couldn't load {}", fileName, e)
            false
        }
    }

    private fun extract(fileName: String): Path {
        val bytes = SteamAPI::class.java.getResourceAsStream("/$fileName")?.use { it.readBytes() }
            ?: error("$fileName isn't in the steamworks4j jar")
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "steamed-natives-${System.getProperty("user.name")}",
            Integer.toHexString(bytes.contentHashCode()))
        val target = dir.resolve(fileName)
        if (Files.exists(target) && Files.size(target) == bytes.size.toLong()) return target
        Files.createDirectories(dir)
        val tmp = Files.createTempFile(dir, fileName, ".tmp")
        Files.write(tmp, bytes)
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        return target
    }
}
