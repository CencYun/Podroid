package com.excp.podroid.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MetricHistoryTest {

    @Test
    fun append_growsUntilMaxThenDropsOldest() {
        var history = emptyList<Float>()
        repeat(3) { i -> history = MetricHistory.append(history, i.toFloat(), max = 3) }
        assertEquals(listOf(0f, 1f, 2f), history)

        history = MetricHistory.append(history, 3f, max = 3)
        assertEquals(listOf(1f, 2f, 3f), history)
    }

    @Test
    fun append_keepsRingBufferAtExactlyMaxSamples() {
        var history = emptyList<Float>()
        repeat(200) { i -> history = MetricHistory.append(history, i.toFloat(), max = 120) }
        assertEquals(120, history.size)
        // The newest 120 values survive, in order; older ones are dropped.
        assertEquals(80f, history.first())
        assertEquals(199f, history.last())
    }

    @Test
    fun append_defaultMaxIsOneHundredTwenty() {
        var history = emptyList<Float>()
        repeat(150) { i -> history = MetricHistory.append(history, i.toFloat()) }
        assertEquals(MetricHistory.MAX_SAMPLES, history.size)
    }

    @Test
    fun append_belowMaxKeepsEverySample() {
        val history = MetricHistory.append(listOf(1f, 2f), 3f, max = 5)
        assertEquals(listOf(1f, 2f, 3f), history)
    }
}
