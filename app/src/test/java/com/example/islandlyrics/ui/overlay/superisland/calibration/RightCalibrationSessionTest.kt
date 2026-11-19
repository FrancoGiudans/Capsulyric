package com.example.islandlyrics.ui.overlay.superisland.calibration

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class RightCalibrationSessionTest {
    private val renderer = Any()
    private val owner = Any()

    @After fun cleanup() {
        RightCalibrationRuntime.stop(owner)
        RightCalibrationRuntime.detach(renderer)
    }

    private fun preview(owner: Any = this.owner) = RightCalibrationRuntime.Preview(
        owner, RightTextCalibration(), "一二三四五六七八", true, false, "device|display|scale"
    )

    @Test fun previewRequiresAnActiveRendererWithRealState() {
        assertFalse(RightCalibrationRuntime.show(preview()))
        RightCalibrationRuntime.attach(renderer, { false }, {})
        assertFalse(RightCalibrationRuntime.show(preview()))
        assertNull(RightCalibrationRuntime.preview)
    }

    @Test fun closingPreviewRequestsImmediateLiveStateRestoration() {
        var refreshes = 0
        RightCalibrationRuntime.attach(renderer, { true }, { refreshes++ })
        assertTrue(RightCalibrationRuntime.show(preview()))
        assertNotNull(RightCalibrationRuntime.preview)
        RightCalibrationRuntime.stop(owner)
        assertNull(RightCalibrationRuntime.preview)
        assertEquals(2, refreshes)
    }

    @Test fun oldEditorCannotStopNewEditorsPreview() {
        val newerOwner = Any()
        RightCalibrationRuntime.attach(renderer, { true }, {})
        RightCalibrationRuntime.show(preview())
        RightCalibrationRuntime.show(preview(newerOwner))
        RightCalibrationRuntime.stop(owner)
        assertSame(newerOwner, RightCalibrationRuntime.preview?.owner)
        RightCalibrationRuntime.stop(newerOwner)
    }

    @Test fun stoppingRendererClearsPreviewAndPreventsFurtherTests() {
        RightCalibrationRuntime.attach(renderer, { true }, {})
        RightCalibrationRuntime.show(preview())
        RightCalibrationRuntime.detach(renderer)
        assertNull(RightCalibrationRuntime.preview)
        assertFalse(RightCalibrationRuntime.show(preview()))
    }

    @Test fun profileCannotApplyToDifferentDisplayOrRingMode() {
        val profile = RightCalibrationProfile(name = "Theme A", environment = "display-A", ring = true,
            calibration = RightTextCalibration())
        assertTrue(profile.matches("display-A", true))
        assertFalse(profile.matches("display-B", true))
        assertFalse(profile.matches("display-A", false))
    }
}
