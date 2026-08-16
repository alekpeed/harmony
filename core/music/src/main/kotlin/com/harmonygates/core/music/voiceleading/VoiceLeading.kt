package com.harmonygates.core.music.voiceleading

import com.harmonygates.core.music.pitch.MidiNote
import com.harmonygates.core.music.voicing.Voicing
import kotlin.math.abs

/** Direction one voice travels between two chords. */
public enum class MotionDirection {
    UP,
    DOWN,
    STATIC,
}

/** Relationship between two voices moving at the same time. */
public enum class MotionType {
    /** Both voices move the same way by the same interval. */
    PARALLEL,

    /** Both voices move the same way by different intervals. */
    SIMILAR,

    /** The voices move in opposite directions. */
    CONTRARY,

    /** One voice holds while the other moves. */
    OBLIQUE,

    /** Neither voice moves. */
    STATIONARY,
}

/** One voice's journey from the first chord to the second. */
public data class VoiceMotion(
    val from: MidiNote,
    val to: MidiNote,
    val semitones: Int,
) {
    public val direction: MotionDirection = when {
        semitones > 0 -> MotionDirection.UP
        semitones < 0 -> MotionDirection.DOWN
        else -> MotionDirection.STATIC
    }

    /** True when the voice does not move at all: a held common tone. */
    public val isCommonTone: Boolean get() = semitones == 0
}

/**
 * Relative costs used when pairing voices between two chords.
 *
 * Adding and dropping a voice are charged separately because they are not the same event
 * musically: a four-note chord answering a three-note one has grown a voice, and pretending
 * some voice leapt an octave to get there would misreport the motion.
 */
public data class VoiceLeadingWeights(
    val addPenalty: Double = 6.0,
    val dropPenalty: Double = 6.0,
    val maxLeapWeight: Double = 0.5,
    val commonToneReward: Double = 1.0,
    val voiceCrossingPenalty: Double = 3.0,
) {
    public companion object {
        public val Default: VoiceLeadingWeights = VoiceLeadingWeights()
    }
}

/** Everything 04_HARMONY_DOMAIN_ENGINE.md §11 asks to be measurable about a chord change. */
public data class VoiceLeadingAnalysis(
    val motions: List<VoiceMotion>,
    /** Notes in the destination that no source voice moved to. */
    val addedVoices: List<MidiNote>,
    /** Notes in the source that reach nothing in the destination. */
    val droppedVoices: List<MidiNote>,
    val totalMotionSemitones: Int,
    val maxLeapSemitones: Int,
    val commonToneCount: Int,
    val pitchClassCommonToneCount: Int,
    val motionTypeCounts: Map<MotionType, Int>,
    val voiceCrossings: Int,
    /** Single number for ranking answers. Lower is smoother. */
    val cost: Double,
) {
    public val contraryMotionCount: Int get() = motionTypeCounts[MotionType.CONTRARY] ?: 0
    public val similarMotionCount: Int get() = motionTypeCounts[MotionType.SIMILAR] ?: 0
    public val parallelMotionCount: Int get() = motionTypeCounts[MotionType.PARALLEL] ?: 0
    public val obliqueMotionCount: Int get() = motionTypeCounts[MotionType.OBLIQUE] ?: 0
}

/**
 * Measures how one voicing moves to another.
 *
 * Voices are paired by solving an assignment problem rather than by index, because index
 * pairing is wrong the moment the two chords have different voice counts — and often wrong
 * even when they do not, since the smoothest reading of a chord change is not always
 * bass-to-bass and top-to-top. Unequal counts are handled with explicit add and drop
 * penalties, exactly as 04_HARMONY_DOMAIN_ENGINE.md §11 requires.
 *
 * The result is deterministic: ties break towards the lower source voice.
 */
public class VoiceLeadingAnalyzer(
    private val weights: VoiceLeadingWeights = VoiceLeadingWeights.Default,
) {

    public fun analyze(from: Voicing, to: Voicing): VoiceLeadingAnalysis =
        analyze(from.pitches, to.pitches)

    public fun analyze(from: List<MidiNote>, to: List<MidiNote>): VoiceLeadingAnalysis {
        require(from.isNotEmpty() && to.isNotEmpty()) { "Voice leading needs notes on both sides" }
        val assignment = assignVoices(from, to)

        val motions = assignment.pairs.map { (sourceIndex, targetIndex) ->
            VoiceMotion(
                from = from[sourceIndex],
                to = to[targetIndex],
                semitones = to[targetIndex].value - from[sourceIndex].value,
            )
        }

        val motionTypes = classifyMotionTypes(motions)
        val crossings = countCrossings(assignment.pairs, from, to)
        val commonTones = motions.count { it.isCommonTone }
        val pitchClassCommonTones = from.map { it.pitchClass }.toSet()
            .intersect(to.map { it.pitchClass }.toSet())
            .size
        val totalMotion = motions.sumOf { abs(it.semitones) }
        val maxLeap = motions.maxOfOrNull { abs(it.semitones) } ?: 0

        val cost = totalMotion +
            maxLeap * weights.maxLeapWeight +
            assignment.added.size * weights.addPenalty +
            assignment.dropped.size * weights.dropPenalty +
            crossings * weights.voiceCrossingPenalty -
            commonTones * weights.commonToneReward

        return VoiceLeadingAnalysis(
            motions = motions,
            addedVoices = assignment.added.map { to[it] },
            droppedVoices = assignment.dropped.map { from[it] },
            totalMotionSemitones = totalMotion,
            maxLeapSemitones = maxLeap,
            commonToneCount = commonTones,
            pitchClassCommonToneCount = pitchClassCommonTones,
            motionTypeCounts = motionTypes,
            voiceCrossings = crossings,
            cost = cost,
        )
    }

    private data class Assignment(
        val pairs: List<Pair<Int, Int>>,
        val added: List<Int>,
        val dropped: List<Int>,
    )

    /**
     * Pairs source voices with destination voices at minimum total cost.
     *
     * The cost matrix is padded to a square with dummy rows and columns priced at the add and
     * drop penalties, which turns "some voices have no partner" into an ordinary assignment
     * problem the Hungarian algorithm already solves.
     */
    private fun assignVoices(from: List<MidiNote>, to: List<MidiNote>): Assignment {
        val size = maxOf(from.size, to.size)
        val cost = Array(size) { row ->
            DoubleArray(size) { column ->
                when {
                    row >= from.size && column >= to.size -> 0.0
                    row >= from.size -> weights.addPenalty
                    column >= to.size -> weights.dropPenalty
                    else -> abs(to[column].value - from[row].value).toDouble()
                }
            }
        }

        val columnForRow = HungarianSolver.solve(cost)
        val pairs = mutableListOf<Pair<Int, Int>>()
        val dropped = mutableListOf<Int>()
        val matchedColumns = mutableSetOf<Int>()

        for (row in from.indices) {
            val column = columnForRow[row]
            if (column < to.size) {
                pairs += row to column
                matchedColumns += column
            } else {
                dropped += row
            }
        }
        val added = to.indices.filterNot { it in matchedColumns }
        return Assignment(pairs.sortedBy { it.first }, added, dropped)
    }

    private fun classifyMotionTypes(motions: List<VoiceMotion>): Map<MotionType, Int> {
        val counts = mutableMapOf<MotionType, Int>()
        for (first in motions.indices) {
            for (second in first + 1 until motions.size) {
                val type = motionTypeOf(motions[first], motions[second])
                counts[type] = (counts[type] ?: 0) + 1
            }
        }
        return counts
    }

    private fun motionTypeOf(first: VoiceMotion, second: VoiceMotion): MotionType = when {
        first.direction == MotionDirection.STATIC && second.direction == MotionDirection.STATIC ->
            MotionType.STATIONARY
        first.direction == MotionDirection.STATIC || second.direction == MotionDirection.STATIC ->
            MotionType.OBLIQUE
        first.direction != second.direction -> MotionType.CONTRARY
        first.semitones == second.semitones -> MotionType.PARALLEL
        else -> MotionType.SIMILAR
    }

    /** Counts pairs of voices whose order swaps between the two chords. */
    private fun countCrossings(
        pairs: List<Pair<Int, Int>>,
        from: List<MidiNote>,
        to: List<MidiNote>,
    ): Int {
        var crossings = 0
        for (first in pairs.indices) {
            for (second in first + 1 until pairs.size) {
                val (sourceA, targetA) = pairs[first]
                val (sourceB, targetB) = pairs[second]
                val sourceOrder = from[sourceA].value.compareTo(from[sourceB].value)
                val targetOrder = to[targetA].value.compareTo(to[targetB].value)
                if (sourceOrder != 0 && targetOrder != 0 && sourceOrder != targetOrder) crossings++
            }
        }
        return crossings
    }
}

/**
 * Minimum-cost assignment on a square matrix, O(n^3).
 *
 * The classic potentials-and-augmenting-paths formulation. Chord voicings are tiny — a dozen
 * voices at the very most — so the cubic bound is irrelevant; what matters is that the answer
 * is the true optimum and is reproducible, since voice-leading scores are shown to players
 * and must not wobble between runs.
 */
internal object HungarianSolver {

    /** Returns, for each row, the column it is assigned to. */
    fun solve(cost: Array<DoubleArray>): IntArray {
        val n = cost.size
        if (n == 0) return IntArray(0)
        require(cost.all { it.size == n }) { "Assignment cost matrix must be square" }

        // 1-based internal arrays; index 0 is the algorithm's virtual starting column.
        val rowPotential = DoubleArray(n + 1)
        val columnPotential = DoubleArray(n + 1)
        val rowForColumn = IntArray(n + 1)
        val previousColumn = IntArray(n + 1)

        for (row in 1..n) {
            rowForColumn[0] = row
            var column = 0
            val minimumSlack = DoubleArray(n + 1) { Double.POSITIVE_INFINITY }
            val used = BooleanArray(n + 1)

            do {
                used[column] = true
                val currentRow = rowForColumn[column]
                var delta = Double.POSITIVE_INFINITY
                var nextColumn = 0

                for (candidate in 1..n) {
                    if (used[candidate]) continue
                    val slack = cost[currentRow - 1][candidate - 1] -
                        rowPotential[currentRow] - columnPotential[candidate]
                    if (slack < minimumSlack[candidate]) {
                        minimumSlack[candidate] = slack
                        previousColumn[candidate] = column
                    }
                    if (minimumSlack[candidate] < delta) {
                        delta = minimumSlack[candidate]
                        nextColumn = candidate
                    }
                }

                for (candidate in 0..n) {
                    if (used[candidate]) {
                        rowPotential[rowForColumn[candidate]] += delta
                        columnPotential[candidate] -= delta
                    } else {
                        minimumSlack[candidate] -= delta
                    }
                }
                column = nextColumn
            } while (rowForColumn[column] != 0)

            do {
                val source = previousColumn[column]
                rowForColumn[column] = rowForColumn[source]
                column = source
            } while (column != 0)
        }

        val columnForRow = IntArray(n)
        for (candidate in 1..n) {
            columnForRow[rowForColumn[candidate] - 1] = candidate - 1
        }
        return columnForRow
    }
}
