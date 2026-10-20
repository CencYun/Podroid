package com.excp.podroid.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkRateSamplerTest {

    /** Sampler with injected clock/byte-counter sources so the math is deterministic off-device. */
    private fun sampler(
        now: () -> Long,
        bytes: () -> Pair<Long, Long>?,
    ) = NetworkRateSampler(nowMs = now, readBytes = bytes)

    @Test
    fun sample_returnsNullOnFirstSample() {
        val s = sampler(now = { 1_000L }, bytes = { 100L to 200L })
        assertNull(s.sample())
    }

    @Test
    fun sample_returnsNullWhenCountersUnreadable() {
        val s = sampler(now = { 1_000L }, bytes = { null })
        assertNull(s.sample())
    }

    @Test
    fun sample_computesRatePerSecond() {
        var now = 0L
        var rx = 0L
        var tx = 0L
        val s = sampler(now = { now }, bytes = { rx to tx })

        assertNull(s.sample()) // warm-up

        now = 1_000L
        rx = 2_000L
        tx = 1_000L
        val sample = s.sample()!!
        assertEquals(2_000f, sample.rxBytesPerSec, 0.01f)
        assertEquals(1_000f, sample.txBytesPerSec, 0.01f)
        assertEquals(2_000L, sample.rxTotalBytes)
        assertEquals(1_000L, sample.txTotalBytes)
    }

    @Test
    fun sample_counterResetGivesNoNegativeRate() {
        var now = 0L
        var rx = 50_000L
        var tx = 50_000L
        val s = sampler(now = { now }, bytes = { rx to tx })

        assertNull(s.sample()) // warm-up

        // Interface counters reset (e.g. reconnect) and now report a smaller value than before.
        now = 1_000L
        rx = 100L
        tx = 100L
        val sample = s.sample()!!
        assertTrue(sample.rxBytesPerSec >= 0f)
        assertTrue(sample.txBytesPerSec >= 0f)
        assertEquals(0f, sample.rxBytesPerSec, 0.01f)
        assertEquals(0f, sample.txBytesPerSec, 0.01f)
    }

    @Test
    fun sample_returnsNullWhenClockDidNotAdvance() {
        val s = sampler(now = { 1_000L }, bytes = { 100L to 100L })
        assertNull(s.sample()) // warm-up
        assertNull(s.sample()) // same timestamp again -> dt <= 0
    }

    @Test
    fun reset_clearsWarmup() {
        var now = 0L
        val s = sampler(now = { now }, bytes = { 100L to 100L })

        assertNull(s.sample()) // warm-up
        now = 1_000L
        s.reset()
        // After reset the next sample is a warm-up again, so it returns null.
        assertNull(s.sample())
    }
}
