package com.example.islandlyrics.ui.overlay.superisland.calibration

import kotlin.math.max

internal enum class CalibrationStage { VISIBILITY, RANGE, LETTERS, DIGITS, MIXED, TAIL, DETAIL, COMPLETE }

internal data class CalibrationProbe(
    val stage: CalibrationStage,
    val sample: String,
    val scrolling: Boolean = false,
    val blocked: Boolean = false
)

/** Deterministic, replayable search. Only explicit complete/incomplete answers advance it. */
internal class RightCalibrationWizard(
    val seed: RightTextCalibration,
    val detailed: Boolean = false,
    val customSample: String = "",
    private val policyFactory: (RightTextCalibration) -> RightTextWidthPolicy
) {
    var calibration = seed.sanitized()
        private set
    private val mutableAnswers = mutableListOf<Boolean>()
    val answers: List<Boolean> get() = mutableAnswers.toList()
    private var lower = 0
    private var upper: Int? = null
    private var candidate = 4
    private var groupIndex = -1
    private var reductions = 0
    private var currentSample: String? = null
    private var blocked = false
    var stage = CalibrationStage.VISIBILITY
        private set
    val rangeUpperBound: Int? get() = upper
    val completed: Boolean get() = stage == CalibrationStage.COMPLETE

    private data class Group(val stage: CalibrationStage, val source: String)
    private val groups = buildList {
        add(Group(CalibrationStage.LETTERS, "WM".repeat(50)))
        add(Group(CalibrationStage.LETTERS, "ilI".repeat(50)))
        add(Group(CalibrationStage.DIGITS, "18".repeat(60)))
        add(Group(CalibrationStage.DIGITS, "0123456789".repeat(15)))
        add(Group(CalibrationStage.MIXED, "一W 1，二i 8。三M!四0 五w 9"))
        add(Group(CalibrationStage.MIXED, "きっと甘えてたの ya Ayayayayayayaya"))
        add(Group(CalibrationStage.TAIL, cjkText(80) + " W1 末"))
        if (customSample.isNotBlank()) add(Group(CalibrationStage.DETAIL, Glyphs.limit(customSample, 160)))
        if (detailed) {
            add(Group(CalibrationStage.DETAIL, "きっと甘えてたの".repeat(8)))
            add(Group(CalibrationStage.DETAIL, "가나다라마바사아자차카타파하".repeat(5)))
            add(Group(CalibrationStage.DETAIL, "A A  A   A 一 二  三终"))
            add(Group(CalibrationStage.DETAIL, "一\u3000二\u3000三\u3000四\u3000终"))
            add(Group(CalibrationStage.DETAIL, "一，二。三！四？五、六；七：终"))
            add(Group(CalibrationStage.DETAIL, "A,B.C!D?E;F:G-H(I)[J]终"))
            add(Group(CalibrationStage.DETAIL, "AaBbCcDdEeFfGgHhIiJjKkLlMmNnOoPpQqRrSsTtUuVvWwXxYyZz"))
            add(Group(CalibrationStage.DETAIL, "一e\u0301二😀三👨‍👩‍👧‍👦四终"))
        }
    }

    fun probe(): CalibrationProbe = when (stage) {
        CalibrationStage.VISIBILITY -> CalibrationProbe(stage, "校准")
        CalibrationStage.RANGE -> CalibrationProbe(stage, cjkText(candidate), blocked = blocked)
        CalibrationStage.COMPLETE -> CalibrationProbe(stage, "")
        else -> CalibrationProbe(stage, currentSample ?: groups[groupIndex].source,
            scrolling = stage == CalibrationStage.TAIL, blocked = blocked)
    }

    fun answer(complete: Boolean): Boolean {
        if (completed || blocked) return false
        mutableAnswers.add(complete)
        when (stage) {
            CalibrationStage.VISIBILITY -> {
                stage = CalibrationStage.RANGE
                candidate = if (complete) 4 else 1
            }
            CalibrationStage.RANGE -> {
                if (complete) lower = candidate else upper = candidate
                if (!complete && candidate == 1) {
                    blocked = true
                } else if ((upper != null && upper!! - lower <= 1) || (complete && candidate == 64)) {
                    calibration = calibration.copy(capacity = lower.toFloat(), margin = 0f).sanitized()
                    advance()
                } else {
                    candidate = upper?.let { (lower + it) / 2 } ?: (candidate * 2).coerceAtMost(64)
                    candidate = candidate.coerceAtLeast(1)
                }
            }
            else -> {
                if (complete) {
                    if (reductions > 0 && stage != CalibrationStage.TAIL) updateBudget()
                    advance()
                } else if (stage == CalibrationStage.TAIL) {
                    val nextMargin = calibration.margin + 0.25f
                    if (calibration.capacity - nextMargin < 1f) blocked = true
                    else calibration = calibration.copy(margin = nextMargin)
                } else {
                    val glyphs = Glyphs.parts(currentSample.orEmpty())
                    if (glyphs.size <= 1) blocked = true else {
                        currentSample = glyphs.dropLast(1).joinToString("").trimEnd()
                        reductions++
                    }
                }
            }
        }
        return true
    }

    private fun updateBudget() {
        val measured = policyFactory(calibration).width(currentSample.orEmpty()).coerceAtLeast(0.05f)
        val multiplier = ((calibration.capacity - calibration.margin) / measured).coerceAtLeast(1f)
        calibration = when (stage) {
            CalibrationStage.LETTERS -> if (calibration.letterMonospace) {
                calibration.copy(letterWidth = calibration.letterWidth * multiplier)
            } else calibration.copy(letterScale = calibration.letterScale * multiplier)
            CalibrationStage.DIGITS -> if (calibration.digitMonospace) {
                calibration.copy(digitWidth = calibration.digitWidth * multiplier)
            } else calibration.copy(digitScale = calibration.digitScale * multiplier)
            else -> calibration.copy(margin = max(calibration.margin, calibration.capacity - measured))
        }.sanitized()
    }

    private fun advance() {
        groupIndex++
        reductions = 0
        if (groupIndex >= groups.size) {
            stage = CalibrationStage.COMPLETE
            currentSample = null
        } else {
            val group = groups[groupIndex]
            stage = group.stage
            currentSample = if (stage == CalibrationStage.TAIL) group.source else {
                policyFactory(calibration).prefix(group.source).trimEnd().ifEmpty { Glyphs.parts(group.source).first() }
            }
        }
    }

    fun scrollingFrames(): List<String> {
        val text = probe().sample
        val window = CalibratedTextWindow(text, policyFactory(calibration))
        return listOf(window.atProgress(0f), window.atProgress(0.5f), window.atProgress(1f))
    }

    companion object {
        fun restore(seed: RightTextCalibration, detailed: Boolean, customSample: String,
            answers: List<Boolean>, policyFactory: (RightTextCalibration) -> RightTextWidthPolicy): RightCalibrationWizard {
            val wizard = RightCalibrationWizard(seed, detailed, customSample, policyFactory)
            answers.take(512).forEach { wizard.answer(it) }
            return wizard
        }

        private fun cjkText(length: Int): String {
            val alphabet = "一二三四五六七八九十甲乙丙丁戊己庚辛壬癸春夏秋冬天地山川风花雪月"
            return buildString { repeat(length) { append(alphabet[it % alphabet.length]) } }
        }
    }
}
