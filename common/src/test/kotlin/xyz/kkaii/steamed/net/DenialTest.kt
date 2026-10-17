package xyz.kkaii.steamed.net

import kotlin.test.Test
import kotlin.test.assertEquals

class DenialTest {
    @Test
    fun `round trips with arguments`() {
        val denial = Denial("steamed.auth.invalid_ticket", listOf("InvalidTicket"))
        assertEquals(denial, Denial.decode(denial.encode()))
    }

    @Test
    fun `older hosts' plain English becomes a key with no arguments`() =
        assertEquals(Denial("You were kicked from this lobby"), Denial.decode("You were kicked from this lobby"))
}
