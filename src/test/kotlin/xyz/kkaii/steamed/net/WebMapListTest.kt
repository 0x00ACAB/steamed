package xyz.kkaii.steamed.net

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WebMapListTest {
    private fun roundTrip(names: List<String>): List<String> {
        val frame = ByteBuffer.wrap(WebMapList.encode(7, names))
        assertEquals(7, frame.get())
        return WebMapList.decode(frame)
    }

    @Test
    fun `round trips`() = assertEquals(listOf("BlueMap", "squaremap"), roundTrip(listOf("BlueMap", "squaremap")))

    @Test
    fun `empty list`() = assertEquals(emptyList(), roundTrip(emptyList()))

    @Test
    fun `names are UTF-8`() = assertEquals(listOf("Kärtchen 🗺"), roundTrip(listOf("Kärtchen 🗺")))

    @Test
    fun `a name longer than its length byte is refused`() {
        assertFailsWith<IllegalArgumentException> { WebMapList.encode(7, listOf("x".repeat(256))) }
    }
}
