package com.harmonygates.core.music.realize

import com.harmonygates.core.music.chord.ChordDegree
import com.harmonygates.core.music.chord.ChordSpec
import com.harmonygates.core.music.pitch.MidiNote
import com.harmonygates.core.music.pitch.PitchClass
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.pitch.SpellingResult
import com.harmonygates.core.music.pitch.atMidiNote
import com.harmonygates.core.music.spelling.AccidentalPolicy
import com.harmonygates.core.music.spelling.DegreeAwarePitchSpeller
import com.harmonygates.core.music.spelling.PitchSpeller
import com.harmonygates.core.music.voicing.BassRequirement
import com.harmonygates.core.music.voicing.TopNoteRequirement
import com.harmonygates.core.music.voicing.VoicedTone
import com.harmonygates.core.music.voicing.Voicing
import com.harmonygates.core.music.voicing.VoicingFamily
import com.harmonygates.core.music.voicing.VoicingPolicy
import com.harmonygates.core.music.voicing.VoicingTransforms
import com.harmonygates.core.music.voicing.voicingOf

/** Turns chord *intent* into chord *sound*. */
public interface ChordRealizer {
    /**
     * The chord's tones, spelled, in stack order.
     *
     * Throws when the chord cannot be written in standard notation. Callers that accept
     * arbitrary input — anything driven by a text field or by authored content — should ask
     * [trySpell] first rather than catching.
     */
    public fun chordTones(spec: ChordSpec): List<SpelledPitchClass>

    /**
     * Spells [degrees] above the chord's root, reporting rather than hiding a spelling that
     * would need a triple accidental.
     */
    public fun trySpell(
        spec: ChordSpec,
        degrees: Collection<ChordDegree> = spec.degrees,
    ): SpellingResult<Map<ChordDegree, SpelledPitchClass>>

    /** Degree-to-spelling map for the chord's full stack. */
    public fun spelledDegrees(spec: ChordSpec): Map<ChordDegree, SpelledPitchClass>

    /** Which role a sounding pitch class plays in [spec], or null if it is not a chord tone. */
    public fun degreeOf(spec: ChordSpec, pitchClass: PitchClass): ChordDegree?

    /**
     * Describes an already-played set of notes in terms of [spec].
     *
     * This is how the evaluator explains a performance and how a generated voicing gets its
     * metadata, so both paths agree by construction.
     */
    public fun analyze(spec: ChordSpec, pitches: List<MidiNote>, family: VoicingFamily? = null): Voicing

    /** Concrete playable renderings of [spec] that satisfy [policy]. */
    public fun generateVoicings(spec: ChordSpec, policy: VoicingPolicy): List<Voicing>
}

/**
 * The production realizer.
 *
 * Voicing generation is deterministic: the same chord and policy always produce the same list
 * in the same order. Phase 4 onwards depends on that for seeded exercise reproduction, and
 * tests depend on it to assert against a stable first candidate.
 */
public class DefaultChordRealizer(
    private val speller: PitchSpeller = DegreeAwarePitchSpeller,
    /** Ceiling on generated candidates, so a wide policy cannot blow up an exercise generator. */
    private val maxVoicings: Int = DEFAULT_MAX_VOICINGS,
) : ChordRealizer {

    override fun chordTones(spec: ChordSpec): List<SpelledPitchClass> =
        spec.degrees.map { degree -> spellOrThrow(spec, degree) }

    override fun spelledDegrees(spec: ChordSpec): Map<ChordDegree, SpelledPitchClass> =
        spec.degrees.associateWith { degree -> spellOrThrow(spec, degree) }

    override fun trySpell(
        spec: ChordSpec,
        degrees: Collection<ChordDegree>,
    ): SpellingResult<Map<ChordDegree, SpelledPitchClass>> {
        val spelled = LinkedHashMap<ChordDegree, SpelledPitchClass>(degrees.size)
        for (degree in degrees.sorted()) {
            when (val result = speller.spell(spec.root, degree)) {
                is SpellingResult.Spelled -> spelled[degree] = result.value
                is SpellingResult.Overflow -> return result
            }
        }
        return SpellingResult.Spelled(spelled)
    }

    override fun degreeOf(spec: ChordSpec, pitchClass: PitchClass): ChordDegree? =
        degreeByPitchClass(spec)[pitchClass]

    /**
     * Describes an already-played set of notes in terms of [spec].
     *
     * This is how the evaluator will later explain a performance and how a generated voicing
     * gets its metadata, so both paths agree by construction.
     */
    override fun analyze(
        spec: ChordSpec,
        pitches: List<MidiNote>,
        family: VoicingFamily?,
    ): Voicing {
        require(pitches.isNotEmpty()) { "Cannot analyse an empty performance as a voicing" }
        val spelledByDegree = spelledDegrees(spec)
        val byPitchClass = degreeByPitchClass(spec)
        val tones = pitches.sorted().map { note ->
            val degree = byPitchClass[note.pitchClass]
            val spelling = degree?.let { spelledByDegree.getValue(it) }
                ?: spec.explicitBass?.takeIf { it.pitchClass == note.pitchClass }
                ?: speller.spellFreely(
                    pitchClass = note.pitchClass,
                    policy = AccidentalPolicy.FROM_KEY_SIGNATURE,
                    keySignatureAccidentals = keySignatureHintFor(spec),
                )
            VoicedTone(spelling.atMidiNote(note), degree)
        }
        return voicingOf(spec, tones, family)
    }

    override fun generateVoicings(spec: ChordSpec, policy: VoicingPolicy): List<Voicing> {
        val effectiveBass = effectiveBassRequirement(spec, policy)
        val range = policy.pitchRange ?: DEFAULT_RANGE

        val candidates = mutableListOf<Voicing>()
        for (degreeSet in degreeSubsets(spec, policy)) {
            val spelled = when (val result = trySpell(spec, degreeSet)) {
                is SpellingResult.Spelled -> result.value
                // An unspellable chord has no valid renderings; the caller sees an empty list
                // rather than a half-correct one.
                is SpellingResult.Overflow -> return emptyList()
            }
            for (bassDegree in bassCandidates(degreeSet, effectiveBass)) {
                candidates += stackFrom(spec, spelled, bassDegree, effectiveBass, range, policy)
            }
        }

        return candidates
            .filter { satisfies(it, policy, range) }
            .distinctBy { voicing -> voicing.pitches.map { it.value } }
            .sortedWith(VOICING_ORDER)
            .take(maxVoicings)
    }

    // --- Generation internals -------------------------------------------------------------

    private fun degreeByPitchClass(spec: ChordSpec): Map<PitchClass, ChordDegree> =
        spelledDegrees(spec).entries
            .groupBy { it.value.pitchClass }
            // When a chord spells one pitch class twice — the b3 and #9 of a blues-flavoured
            // dominant, say — the lower stack position is the one a player is answering.
            .mapValues { (_, entries) -> entries.minOf { it.key } }

    /**
     * A slash chord carries a bass instruction inside the symbol. Honour it when the exercise
     * policy has not already said something more specific.
     */
    private fun effectiveBassRequirement(spec: ChordSpec, policy: VoicingPolicy): BassRequirement {
        val explicit = spec.explicitBass
        return if (explicit != null && policy.bassRequirement == BassRequirement.Unconstrained) {
            BassRequirement.PitchClassInBass(explicit)
        } else {
            policy.bassRequirement
        }
    }

    /**
     * Degree combinations worth voicing: everything required, plus each subset of the optional
     * tones. Subsets are enumerated in a fixed order so results stay stable across runs.
     */
    private fun degreeSubsets(spec: ChordSpec, policy: VoicingPolicy): List<Set<ChordDegree>> {
        val available = spec.degrees.toSet()
        val required = policy.requiredDegrees.ifEmpty { spec.requiredDegrees }
        val optional = ((policy.optionalDegrees.ifEmpty { spec.optionalDegrees } intersect available) - required)
            .sorted()
            .take(MAX_OPTIONAL_DEGREES_ENUMERATED)

        val subsets = mutableListOf<Set<ChordDegree>>()
        for (mask in 0 until (1 shl optional.size)) {
            val chosen = optional.filterIndexed { index, _ -> (mask shr index) and 1 == 1 }
            val combined = required + chosen
            if (policy.maxVoices != null && combined.size > policy.maxVoices) continue
            subsets += combined
        }
        return subsets.sortedBy { it.size }
    }

    private fun bassCandidates(
        degrees: Set<ChordDegree>,
        requirement: BassRequirement,
    ): List<ChordDegree?> = when (requirement) {
        BassRequirement.Unconstrained -> degrees.sorted()
        BassRequirement.RootInBass -> listOf(ChordDegree.ROOT).filter { it in degrees }
        is BassRequirement.DegreeInBass -> listOf(requirement.degree).filter { it in degrees }
        is BassRequirement.DegreeNotInBass -> degrees.sorted().filter { it != requirement.degree }
        // Null means "the bass is a note below the chord proper" — the C/A case, where the
        // bass may not be a chord tone at all.
        is BassRequirement.PitchClassInBass -> listOf(null)
    }

    /**
     * Builds close-position stacks: the bass tone, then every other tone at the lowest octave
     * that keeps the voicing ascending. Wider families are produced from these by
     * [VoicingTransforms].
     */
    private fun stackFrom(
        spec: ChordSpec,
        spelled: Map<ChordDegree, SpelledPitchClass>,
        bassDegree: ChordDegree?,
        bassRequirement: BassRequirement,
        range: IntRange,
        policy: VoicingPolicy,
    ): List<Voicing> {
        val bassPitchClass = when {
            bassDegree != null -> spelled.getValue(bassDegree)
            bassRequirement is BassRequirement.PitchClassInBass -> bassRequirement.pitchClass
            else -> return emptyList()
        }
        val upperDegrees = spelled.keys
            .filter { it != bassDegree }
            .sortedBy { stackPositionAbove(it, bassDegree) }

        val results = mutableListOf<Voicing>()
        for (bassNote in notesOfPitchClass(bassPitchClass, range)) {
            var previous = bassNote.value
            val notes = mutableListOf(bassNote.value)
            var fits = true
            for (degree in upperDegrees) {
                val next = nextAbove(previous, spelled.getValue(degree))
                if (next > range.last) {
                    fits = false
                    break
                }
                notes += next
                previous = next
            }
            if (!fits) continue
            val closeVoicing = analyze(spec, notes.map { MidiNote(it) }, VoicingFamily.CLOSE)
            results += closeVoicing
            policy.namedFamily?.let { family -> results += transformInto(closeVoicing, family) }
        }
        return results
    }

    private fun transformInto(voicing: Voicing, family: VoicingFamily): List<Voicing> =
        when (family) {
            VoicingFamily.CLOSE -> emptyList()
            VoicingFamily.DROP_2 -> listOfNotNull(VoicingTransforms.drop2(voicing))
            VoicingFamily.DROP_3 -> listOfNotNull(VoicingTransforms.drop3(voicing))
            VoicingFamily.DROP_2_AND_4 -> listOfNotNull(VoicingTransforms.drop2And4(voicing))
            VoicingFamily.SPREAD -> listOfNotNull(VoicingTransforms.spread(voicing))
            // The shell, rootless and quartal families are selected by degree content rather
            // than by reshaping a close voicing, so the policy's required degrees already
            // describe them. Region 7 and 9 gates configure them that way.
            else -> emptyList()
        }

    /** Orders upper tones as a stack starting from the bass degree and wrapping around. */
    private fun stackPositionAbove(degree: ChordDegree, bassDegree: ChordDegree?): Int {
        val bassPosition = bassDegree?.number ?: 0
        return if (degree.number >= bassPosition) degree.number else degree.number + STACK_WRAP
    }

    private fun notesOfPitchClass(pitchClass: SpelledPitchClass, range: IntRange): List<MidiNote> {
        val target = pitchClass.pitchClass.value
        return range.filter { it.mod(12) == target }.map { MidiNote(it) }
    }

    private fun nextAbove(previous: Int, pitchClass: SpelledPitchClass): Int {
        val target = pitchClass.pitchClass.value
        var candidate = previous + 1
        while (candidate.mod(12) != target) candidate++
        return candidate
    }

    private fun satisfies(voicing: Voicing, policy: VoicingPolicy, range: IntRange): Boolean {
        if (voicing.pitches.any { it.value !in range }) return false
        if (policy.maxVoices != null && voicing.voiceCount > policy.maxVoices) return false
        if (!policy.allowDoubling && voicing.metadata.doubledDegrees.isNotEmpty()) return false
        if (policy.requireRoot && voicing.metadata.isRootless) return false
        if (voicing.metadata.includedDegrees.any { it in policy.disallowedDegrees }) return false
        if (!policy.requiredDegrees.all { it in voicing.metadata.includedDegrees }) return false
        val top = policy.topNoteRequirement
        return top == null || satisfiesTop(voicing, top)
    }

    private fun satisfiesTop(voicing: Voicing, requirement: TopNoteRequirement): Boolean =
        when (requirement) {
            is TopNoteRequirement.DegreeOnTop -> voicing.metadata.topDegree == requirement.degree
            is TopNoteRequirement.ExactNoteOnTop -> voicing.top == requirement.note
            is TopNoteRequirement.PitchClassOnTop -> voicing.top.pitchClass == requirement.pitchClass
        }

    private fun spellOrThrow(spec: ChordSpec, degree: ChordDegree): SpelledPitchClass =
        when (val result = speller.spell(spec.root, degree)) {
            is SpellingResult.Spelled -> result.value
            is SpellingResult.Overflow -> error(
                "${spec.symbol} cannot be spelled: ${result.message}. " +
                    "Choose an enharmonic root for this content.",
            )
        }

    /** Flat roots imply a flat key for any non-chord tone that needs a free spelling. */
    private fun keySignatureHintFor(spec: ChordSpec): Int =
        if (spec.root.accidental.offset < 0) -FREE_SPELLING_KEY_HINT else FREE_SPELLING_KEY_HINT

    public companion object {
        public const val DEFAULT_MAX_VOICINGS: Int = 64

        /** Two octaves either side of middle C: the practical two-hand keyboard span. */
        public val DEFAULT_RANGE: IntRange = 36..96

        /** Caps subset enumeration at 2^6 combinations for a pathologically wide policy. */
        private const val MAX_OPTIONAL_DEGREES_ENUMERATED = 6

        /** Two octaves of degree numbering, so a 13th above a 3rd sorts after it. */
        private const val STACK_WRAP = 14

        private const val FREE_SPELLING_KEY_HINT = 3

        /** Tightest first, then lowest, then note-by-note. Ties never resolve at random. */
        private val VOICING_ORDER: Comparator<Voicing> = compareBy(
            { it.metadata.spanSemitones },
            { it.bass.value },
            { voicing -> voicing.pitches.joinToString(",") { it.value.toString().padStart(3, '0') } },
        )
    }
}
