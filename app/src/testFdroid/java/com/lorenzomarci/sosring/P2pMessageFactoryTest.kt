package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class P2pMessageFactoryTest {

    @Test
    fun locRequest_hasLocRequestType() {
        assertEquals("pos_req", P2pMessageFactory.type(P2pMessageFactory.locRequest()))
    }

    @Test
    fun locResponse_roundTrip() {
        val bytes = P2pMessageFactory.locResponse(45.123456, 11.654321, 12.0)

        assertEquals("pos_res", P2pMessageFactory.type(bytes))
        val parsed = P2pMessageFactory.parseLocResponse(bytes)!!
        assertEquals(45.123456, parsed.lat, 1e-9)
        assertEquals(11.654321, parsed.lon, 1e-9)
        assertEquals(12.0, parsed.accuracy, 1e-9)
    }

    @Test
    fun type_returnsNullOnGarbage() {
        assertNull(P2pMessageFactory.type("not json".toByteArray()))
    }

    @Test
    fun parseLocResponse_rejectsWrongType() {
        assertNull(P2pMessageFactory.parseLocResponse(P2pMessageFactory.locRequest()))
    }

    @Test
    fun locRequest_carriesTimestamp() {
        assertEquals(123456789L, P2pMessageFactory.timestamp(P2pMessageFactory.locRequest(123456789L)))
    }

    @Test
    fun locResponse_carriesTimestamp() {
        val bytes = P2pMessageFactory.locResponse(1.0, 2.0, 3.0, 987654321L)
        assertEquals(987654321L, P2pMessageFactory.timestamp(bytes))
    }

    @Test
    fun timestamp_returnsNullWhenAbsent() {
        assertNull(P2pMessageFactory.timestamp("{\"type\":\"pos_req\"}".toByteArray()))
    }

    @Test
    fun parseLocResponse_rejectsOutOfRangeLatitude() {
        assertNull(P2pMessageFactory.parseLocResponse(P2pMessageFactory.locResponse(91.0, 0.0, 1.0)))
    }

    @Test
    fun parseLocResponse_rejectsOutOfRangeLongitude() {
        assertNull(P2pMessageFactory.parseLocResponse(P2pMessageFactory.locResponse(0.0, 181.0, 1.0)))
    }

    @Test
    fun parseLocResponse_acceptsBoundaryCoordinates() {
        val parsed = P2pMessageFactory.parseLocResponse(P2pMessageFactory.locResponse(-90.0, 180.0, 5.0))!!
        assertEquals(-90.0, parsed.lat, 1e-9)
        assertEquals(180.0, parsed.lon, 1e-9)
    }

    @Test
    fun liveStart_roundTrip() {
        val bytes = P2pMessageFactory.liveStart("sid-1", 15, 10, 111L)
        assertEquals("liv_beg", P2pMessageFactory.type(bytes))
        assertEquals(111L, P2pMessageFactory.timestamp(bytes))
        val parsed = P2pMessageFactory.parseLiveStart(bytes)!!
        assertEquals("sid-1", parsed.sessionId)
        assertEquals(15, parsed.durationMin)
        assertEquals(10, parsed.intervalSec)
    }

    @Test
    fun parseLiveStart_coercesOutOfRange() {
        val parsed = P2pMessageFactory.parseLiveStart(P2pMessageFactory.liveStart("s", 999, 1))!!
        assertEquals(60, parsed.durationMin)
        assertEquals(5, parsed.intervalSec)
    }

    @Test
    fun parseLiveStart_rejectsBlankSession() {
        assertNull(P2pMessageFactory.parseLiveStart(P2pMessageFactory.liveStart("", 15, 10)))
    }

    @Test
    fun livePoint_roundTrip() {
        val bytes = P2pMessageFactory.livePoint("sid-2", 45.5, 11.2, 8.0, 222L)
        assertEquals("liv_pos", P2pMessageFactory.type(bytes))
        assertEquals(222L, P2pMessageFactory.timestamp(bytes))
        val parsed = P2pMessageFactory.parseLivePoint(bytes)!!
        assertEquals("sid-2", parsed.sessionId)
        assertEquals(45.5, parsed.lat, 1e-9)
        assertEquals(11.2, parsed.lon, 1e-9)
        assertEquals(8.0, parsed.accuracy, 1e-9)
    }

    @Test
    fun parseLivePoint_rejectsOutOfRange() {
        assertNull(P2pMessageFactory.parseLivePoint(P2pMessageFactory.livePoint("s", 91.0, 0.0, 1.0)))
        assertNull(P2pMessageFactory.parseLivePoint(P2pMessageFactory.livePoint("s", 0.0, 181.0, 1.0)))
    }

    @Test
    fun parseLivePoint_rejectsBlankSession() {
        assertNull(P2pMessageFactory.parseLivePoint(P2pMessageFactory.livePoint("", 1.0, 2.0, 1.0)))
    }

    @Test
    fun liveStop_roundTrip() {
        val bytes = P2pMessageFactory.liveStop("sid-3", 333L)
        assertEquals("liv_end", P2pMessageFactory.type(bytes))
        assertEquals(333L, P2pMessageFactory.timestamp(bytes))
        assertEquals("sid-3", P2pMessageFactory.parseLiveStop(bytes)!!.sessionId)
    }

    @Test
    fun parseLiveStop_rejectsWrongType() {
        assertNull(P2pMessageFactory.parseLiveStop(P2pMessageFactory.locRequest()))
    }
}
