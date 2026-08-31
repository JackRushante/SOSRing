package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleMessagesVipResolverTest {

    private val contacts = listOf(
        VipContact("One", "+393331112222"),
        VipContact("Two", "+393334445555")
    )

    @Test
    fun matchesOneVipByNumber() {
        assertEquals(
            contacts[0],
            GoogleMessagesVipResolver.uniqueVip(setOf("3331112222"), contacts)
        )
    }

    @Test
    fun rejectsMissingOrAmbiguousMatch() {
        assertNull(GoogleMessagesVipResolver.uniqueVip(emptySet(), contacts))
        assertNull(
            GoogleMessagesVipResolver.uniqueVip(
                setOf("+393331112222", "+393334445555"),
                contacts
            )
        )
    }
}
