package xyz.kkaii.steamed.net

import kotlin.test.Test
import kotlin.test.assertEquals

class VoicePortsTest {
    @Test
    fun `round trips`() = assertEquals(listOf(24454, 25565), VoicePorts.decode(VoicePorts.encode(listOf(24454, 25565))))

    @Test
    fun `no voice mods means an empty reply`() {
        assertEquals(0, VoicePorts.encode(emptyList()).size)
        assertEquals(emptyList(), VoicePorts.decode(ByteArray(0)))
    }

    @Test
    fun `junk and out of range ports are dropped`() =
        assertEquals(listOf(8080), VoicePorts.decode("x,8080,,0,70000,-1".toByteArray()))
}
