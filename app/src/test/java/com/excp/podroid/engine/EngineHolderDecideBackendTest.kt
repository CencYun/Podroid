/*
 * Podroid - Rootless Podman for Android
 * Copyright (C) 2024-2026 Podroid contributors
 */
package com.excp.podroid.engine

import com.excp.podroid.R
import com.excp.podroid.engine.EngineHolder.Companion.FallbackReason
import com.excp.podroid.engine.EngineHolder.Companion.decideBackend
import com.excp.podroid.engine.EngineHolder.Companion.fallbackReasonResId
import com.excp.podroid.engine.EngineHolder.Companion.shouldProbeNetworkFeature
import com.excp.podroid.engine.avf.AvfReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the pure backend-selection decision extracted from EngineHolder.pick()
 * (#66). The decision itself must never change here - only its exposure as a
 * testable function - so these cases mirror pick()'s old inline `when`
 * branch-for-branch.
 */
class EngineHolderDecideBackendTest {

    @Test
    fun `auto selection with usable AVF picks avf and no fallback`() {
        val (backendId, reason) = decideBackend(
            selection = EngineSelection.AUTO,
            avfUsable = true,
            protectedOnly = false,
            networkAvailable = true,
        )
        assertEquals("avf", backendId)
        assertNull(reason)
    }

    @Test
    fun `auto selection with unusable AVF picks qemu and no fallback`() {
        // AUTO never surfaces a fallback reason: falling through to QEMU is the
        // normal AUTO behavior, not a forced selection that failed.
        val (backendId, reason) = decideBackend(
            selection = EngineSelection.AUTO,
            avfUsable = false,
            protectedOnly = false,
            networkAvailable = true,
        )
        assertEquals("qemu", backendId)
        assertNull(reason)
    }

    @Test
    fun `forced avf with unusable AVF falls back to qemu with a reason`() {
        val (backendId, reason) = decideBackend(
            selection = EngineSelection.AVF,
            avfUsable = false,
            protectedOnly = false,
            networkAvailable = true,
        )
        assertEquals("qemu", backendId)
        assertEquals(FallbackReason.UNAVAILABLE, reason)
    }

    @Test
    fun `forced avf with usable AVF picks avf and no fallback`() {
        val (backendId, reason) = decideBackend(
            selection = EngineSelection.AVF,
            avfUsable = true,
            protectedOnly = false,
            networkAvailable = true,
        )
        assertEquals("avf", backendId)
        assertNull(reason)
    }

    @Test
    fun `forced qemu always picks qemu regardless of AVF usability`() {
        val (backendId, reason) = decideBackend(
            selection = EngineSelection.QEMU,
            avfUsable = true,
            protectedOnly = true,
            networkAvailable = true,
        )
        assertEquals("qemu", backendId)
        assertNull(reason)
    }

    @Test
    fun `forced avf on a protected-only device gets the protected-only reason`() {
        val (backendId, reason) = decideBackend(
            selection = EngineSelection.AVF,
            avfUsable = false,
            protectedOnly = true,
            networkAvailable = true,
        )
        assertEquals("qemu", backendId)
        assertEquals(FallbackReason.PROTECTED_ONLY, reason)
    }

    @Test
    fun `auto selection with network compiled out falls back to qemu with the network reason`() {
        // #43: OEM build has isFeatureEnabled(com.android.kvm.NETWORK) == false.
        val (backendId, reason) = decideBackend(
            selection = EngineSelection.AUTO,
            avfUsable = true,
            protectedOnly = false,
            networkAvailable = false,
        )
        assertEquals("qemu", backendId)
        assertEquals(FallbackReason.NETWORK_DISABLED, reason)
    }

    @Test
    fun `auto selection with unknown network support picks avf`() {
        // Missing method or a throw resolves to networkAvailable = true before
        // reaching decideBackend, so an "unknown" device behaves like before #43.
        val (backendId, reason) = decideBackend(
            selection = EngineSelection.AUTO,
            avfUsable = true,
            protectedOnly = false,
            networkAvailable = true,
        )
        assertEquals("avf", backendId)
        assertNull(reason)
    }

    @Test
    fun `forced avf with network compiled out stays avf`() {
        // The user explicitly asked for AVF; #43's check only steers AUTO.
        val (backendId, reason) = decideBackend(
            selection = EngineSelection.AVF,
            avfUsable = true,
            protectedOnly = false,
            networkAvailable = false,
        )
        assertEquals("avf", backendId)
        assertNull(reason)
    }

    @Test
    fun `network-disabled fallback maps to its dedicated resource`() {
        assertEquals(
            R.string.backend_fallback_reason_network,
            fallbackReasonResId(
                FallbackReason.NETWORK_DISABLED,
                report(),
            ),
        )
    }

    @Test
    fun `protected-only fallback maps to its dedicated resource`() {
        assertEquals(
            R.string.backend_fallback_reason_protected_only,
            fallbackReasonResId(
                FallbackReason.PROTECTED_ONLY,
                report(featureSupported = false),
            ),
        )
    }

    @Test
    fun `unavailable fallback reports the missing feature first`() {
        assertEquals(
            R.string.backend_fallback_reason_feature,
            fallbackReasonResId(
                FallbackReason.UNAVAILABLE,
                report(featureSupported = false),
            ),
        )
    }

    @Test
    fun `unavailable fallback reports missing permissions before service`() {
        assertEquals(
            R.string.backend_fallback_reason_permissions,
            fallbackReasonResId(
                FallbackReason.UNAVAILABLE,
                report(managePermissionGranted = false, serviceReachable = false),
            ),
        )
    }

    @Test
    fun `unavailable fallback reports service when prerequisites are present`() {
        assertEquals(
            R.string.backend_fallback_reason_service,
            fallbackReasonResId(
                FallbackReason.UNAVAILABLE,
                report(serviceReachable = false),
            ),
        )
    }

    @Test
    fun `network feature probe runs only for auto with avf otherwise usable`() {
        // #43 follow-up (T5b): the probe is a binder IPC, so it must be
        // skipped whenever it cannot change decideBackend's outcome.
        assertTrue(shouldProbeNetworkFeature(EngineSelection.AUTO, avfUsable = true))
    }

    @Test
    fun `network feature probe skipped for auto when avf is not usable anyway`() {
        assertFalse(shouldProbeNetworkFeature(EngineSelection.AUTO, avfUsable = false))
    }

    @Test
    fun `network feature probe skipped for forced avf`() {
        assertFalse(shouldProbeNetworkFeature(EngineSelection.AVF, avfUsable = true))
    }

    @Test
    fun `network feature probe skipped for forced qemu`() {
        assertFalse(shouldProbeNetworkFeature(EngineSelection.QEMU, avfUsable = true))
    }

    private fun report(
        featureSupported: Boolean = true,
        managePermissionGranted: Boolean = true,
        customPermissionGranted: Boolean = true,
        serviceReachable: Boolean = true,
    ) = AvfReport(
        featureSupported = featureSupported,
        managePermissionGranted = managePermissionGranted,
        customPermissionGranted = customPermissionGranted,
        virtApexPresent = true,
        managerClassPresent = true,
        serviceReachable = serviceReachable,
        customVmConfigSupported = true,
        smokeTestResult = null,
    )
}
