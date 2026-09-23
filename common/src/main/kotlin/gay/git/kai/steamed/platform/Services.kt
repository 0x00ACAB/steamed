package gay.git.kai.steamed.platform

import java.util.ServiceLoader

object Services {
    val PLATFORM: IPlatformHelper = ServiceLoader.load(IPlatformHelper::class.java, Services::class.java.classLoader)
        .findFirst()
        .orElseThrow { NullPointerException("No IPlatformHelper implementation found") }
}
