package com.example.islandlyrics.ui.overlay.superisland.calibration

import org.junit.Assert.*
import org.junit.Test

class RightCalibrationWizardTest {
    private fun policy(c: RightTextCalibration) = RightTextWidthPolicy(c) { text ->
        Glyphs.parts(text).sumOf { 0.5 }.toFloat()
    }
    private fun wizard(detailed: Boolean = false, custom: String = "") =
        RightCalibrationWizard(RightTextCalibration(), detailed, custom, ::policy)

    private fun run(w: RightCalibrationWizard, capacity: Float = 7f): RightCalibrationWizard {
        var count = 0
        val oracle = policy(RightTextCalibration(capacity = capacity))
        while (!w.completed && count++ < 160) {
            val probe = w.probe()
            assertFalse(probe.blocked)
            val fits = if (probe.scrolling) w.scrollingFrames().all(oracle::fits) else oracle.fits(probe.sample)
            assertTrue(w.answer(fits))
        }
        assertTrue("Did not converge in $count answers", w.completed)
        return w
    }

    @Test fun normalBasicRunNeedsTwelveToSixteenJudgments() {
        val w = run(wizard())
        assertEquals(7f, w.calibration.capacity, 0.001f)
        assertTrue(w.answers.size in 12..16)
    }

    @Test fun rangeSearchWorksBelowTheOldMinimumAndAtTheSearchLimit() {
        for (capacity in listOf(1f, 3f, 6f, 8f, 14f, 32f, 64f)) {
            val w = wizard()
            while (w.stage == CalibrationStage.VISIBILITY || w.stage == CalibrationStage.RANGE) {
                w.answer(policy(RightTextCalibration(capacity = capacity)).fits(w.probe().sample))
            }
            assertEquals(capacity, w.calibration.capacity, 0.001f)
            if (capacity == 64f) assertNull(w.rangeUpperBound)
        }
    }

    @Test fun replayingAnswersRestoresExactTrialAndResults() {
        val original = wizard()
        repeat(6) {
            val p = original.probe()
            original.answer(policy(RightTextCalibration()).fits(p.sample))
            val restored = RightCalibrationWizard.restore(original.seed, false, "", original.answers, ::policy)
            assertEquals(original.probe(), restored.probe())
            assertEquals(original.calibration, restored.calibration)
        }
    }

    @Test fun undoReturnsToPreviousUnansweredTrial() {
        val original = wizard()
        original.answer(true)
        val previous = original.probe()
        original.answer(true)
        val restored = RightCalibrationWizard.restore(original.seed, false, "", original.answers.dropLast(1), ::policy)
        assertEquals(previous, restored.probe())
    }

    @Test fun detailedContinuationKeepsBasicAndCustomAnswersInTheirOriginalOrder() {
        val basic = run(wizard(custom = "自选歌词 W1。"))
        val detailed = RightCalibrationWizard.restore(basic.seed, true, basic.customSample, basic.answers, ::policy)
        assertEquals(CalibrationStage.DETAIL, detailed.stage)
        assertTrue(detailed.probe().sample.startsWith("きっと"))
        assertEquals(basic.answers, detailed.answers)
        run(detailed)
    }

    @Test fun clippingShortensThePresentedTextBeforeAdvancing() {
        val w = wizard()
        while (w.stage != CalibrationStage.LETTERS) w.answer(w.probe().sample.length <= 7)
        val before = w.probe().sample
        w.answer(false)
        assertEquals(CalibrationStage.LETTERS, w.stage)
        assertTrue(before.startsWith(w.probe().sample))
        assertTrue(w.probe().sample.length < before.length)
        w.answer(true)
        assertTrue(w.calibration.letterScale > 1f)
    }

    @Test fun aSingleUnrenderableGlyphBlocksInsteadOfInventingACapacity() {
        val w = wizard()
        w.answer(false)
        w.answer(false)
        assertTrue(w.probe().blocked)
        val answers = w.answers
        assertFalse(w.answer(true))
        assertEquals(answers, w.answers)
    }

    @Test fun tailFailureAddsFeedbackBasedReserveAndKeepsTheTailStage() {
        val w = wizard()
        while (w.stage != CalibrationStage.TAIL) w.answer(policy(RightTextCalibration()).fits(w.probe().sample))
        val margin = w.calibration.margin
        w.answer(false)
        assertEquals(CalibrationStage.TAIL, w.stage)
        assertTrue(w.calibration.margin > margin)
        assertTrue(w.scrollingFrames().last().endsWith("末"))
    }
}
