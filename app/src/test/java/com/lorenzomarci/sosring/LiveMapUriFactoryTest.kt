package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LiveMapUriFactoryTest {

    @Test
    fun multipleLivePointsOpenLatestPointNotDirectionsRoute() {
        val points = listOf(
            LocationPoint(1, "+391", "s1", 37.0, 15.0, 5f, 1000),
            LocationPoint(2, "+391", "s1", 37.1, 15.1, 5f, 2000)
        )

        val uri = LiveMapUriFactory.latestPointUri(points, "Clizia")

        assertEquals("geo:37.1,15.1?q=37.1,15.1(Clizia)", uri)
        assertFalse(uri.contains("/dir/"))
    }

    @Test
    fun latestPointUri_emptyList_returnsEmpty() {
        assertEquals("", LiveMapUriFactory.latestPointUri(emptyList(), "X"))
    }
}
