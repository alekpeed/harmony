package com.harmonygates.core.music.progression

import com.harmonygates.core.music.chord.ChordDegree
import com.harmonygates.core.music.chord.ChordSpec
import com.harmonygates.core.music.key.FunctionalChord
import com.harmonygates.core.music.key.Functions
import com.harmonygates.core.music.key.KeyContext
import com.harmonygates.core.music.key.Mode
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.pitch.SpellingResult
import com.harmonygates.core.music.random.DefaultSeededRandomFactory
import com.harmonygates.core.music.random.RandomSource
import com.harmonygates.core.music.random.SeededRandomFactory
import com.harmonygates.core.music.voicing.BassRequirement
import com.harmonygates.core.music.voicing.VoicingFamilies
import com.harmonygates.core.music.voicing.VoicingFamily
import com.harmonygates.core.music.voicing.VoicingPolicy

/**
 * How much freedom a run gives the player over the shape of each chord.
 *
 * This is the "acceptable voicing family" of the handoff's chord-event contract, expressed as
 * the choice a curriculum author actually makes: not a list of family ids per chord, but one
 * decision for the run, applied to every chord in it.
 */
public enum class VoicingStyle(public val label: String) {
    /** Any correct rendering. Inversion, register and doublings are all free. */
    ANY_VOICING("Any voicing"),

    /** The chord must be in root position. Everything above the bass is free. */
    ROOT_POSITION("Root position"),

    /** Root, third, seventh, seventh on top. */
    SHELL("Shell voicings"),

    /** Third and seventh alone. */
    GUIDE_TONES("Guide tones"),

    /** Left-hand rootless A: third lowest. */
    ROOTLESS_A("Rootless A"),

    /** Left-hand rootless B: seventh lowest. */
    ROOTLESS_B("Rootless B"),
    ;

    internal val family: VoicingFamily?
        get() = when (this) {
            ANY_VOICING, ROOT_POSITION -> null
            SHELL -> VoicingFamily.SHELL_1_3_7
            GUIDE_TONES -> VoicingFamily.GUIDE_TONES
            ROOTLESS_A -> VoicingFamily.ROOTLESS_A
            ROOTLESS_B -> VoicingFamily.ROOTLESS_B
        }
}

/** A progression written as functions, ready to be placed in any key. */
public data class ProgressionTemplate(
    val id: String,
    val title: String,
    val functions: List<FunctionalChord>,
    val mode: Mode = Mode.MAJOR,
    val loop: Boolean = false,
    val beatsPerChord: Int = ChordEvent.DEFAULT_DURATION_BEATS,
) {
    init {
        require(functions.isNotEmpty()) { "A progression template needs at least one chord" }
    }
}

/**
 * The functional vocabulary of Region 12, as templates.
 *
 * These reuse [Functions] rather than restating chords: a `ii-V-I` is two lists in the codebase
 * only if someone writes it twice.
 */
public object ProgressionTemplates {

    public val MajorTwoFiveOne: ProgressionTemplate = ProgressionTemplate(
        id = "progression.major_two_five_one",
        title = "Major ii-V-I",
        functions = Functions.MajorTwoFiveOne,
    )

    public val MinorTwoFiveOne: ProgressionTemplate = ProgressionTemplate(
        id = "progression.minor_two_five_one",
        title = "Minor ii-V-i",
        functions = Functions.MinorTwoFiveOne,
        mode = Mode.HARMONIC_MINOR,
    )

    public val Turnaround: ProgressionTemplate = ProgressionTemplate(
        id = "progression.one_six_two_five",
        title = "I-vi-ii-V turnaround",
        functions = Functions.OneSixTwoFive,
        loop = true,
    )

    public val TritoneSubTurnaround: ProgressionTemplate = ProgressionTemplate(
        id = "progression.tritone_sub_turnaround",
        title = "ii-bII7-I",
        functions = Functions.TritoneSubTurnaround,
    )

    public val BackdoorCadence: ProgressionTemplate = ProgressionTemplate(
        id = "progression.backdoor",
        title = "Backdoor cadence",
        functions = Functions.BackdoorCadence,
    )

    public val all: List<ProgressionTemplate> = listOf(
        MajorTwoFiveOne,
        MinorTwoFiveOne,
        Turnaround,
        TritoneSubTurnaround,
        BackdoorCadence,
    )
}

/** Builds runnable progressions out of templates. */
public interface ProgressionGenerator {

    /** Places [template] in [key], voiced according to [style]. */
    public fun generate(
        template: ProgressionTemplate,
        key: KeyContext,
        style: VoicingStyle = VoicingStyle.ANY_VOICING,
    ): SpellingResult<Progression>

    /**
     * The same template through every key, back to back.
     *
     * The long-form drill of Region 12, and the case the Progression Run renderer exists for:
     * a single run of arbitrary length that no fixed number of orbs could hold.
     */
    public fun throughAllKeys(
        template: ProgressionTemplate,
        style: VoicingStyle = VoicingStyle.ANY_VOICING,
        order: KeyOrder = KeyOrder.CYCLE_OF_FOURTHS,
        seed: Long? = null,
    ): Progression
}

/** The order the keys of a multi-key drill arrive in. */
public enum class KeyOrder {
    /** Down a fourth each time: the way tunes move. */
    CYCLE_OF_FOURTHS,

    /** Up a semitone each time. */
    CHROMATIC,

    /** Shuffled from a seed, so a drill is reproducible without being predictable. */
    SEEDED_SHUFFLE,
}

/**
 * The production generator.
 *
 * Deterministic throughout: the same template, key and style always produce the same events in
 * the same order, and a seeded shuffle reproduces exactly. Phase 5 stores run results against
 * these ids, so a run that cannot be rebuilt from its inputs would lose its own history.
 */
public class DefaultProgressionGenerator(
    private val randomFactory: SeededRandomFactory = DefaultSeededRandomFactory,
) : ProgressionGenerator {

    override fun generate(
        template: ProgressionTemplate,
        key: KeyContext,
        style: VoicingStyle,
    ): SpellingResult<Progression> {
        val events = mutableListOf<ChordEvent>()
        template.functions.forEachIndexed { index, function ->
            val chord = when (val resolved = function.resolveIn(key.copy(mode = template.mode))) {
                is SpellingResult.Spelled -> resolved.value
                is SpellingResult.Overflow -> return resolved
            }
            events += eventFor(
                id = "${template.id}.${key.tonic}.$index",
                chord = chord,
                function = function,
                style = style,
                beats = template.beatsPerChord,
            )
        }
        return SpellingResult.Spelled(
            Progression(
                id = "${template.id}.${key.tonic}.${style.name.lowercase()}",
                title = "${template.title} in ${key.tonic}",
                key = key,
                events = events,
                source = ProgressionSource.PRESET,
                loop = template.loop,
            ),
        )
    }

    override fun throughAllKeys(
        template: ProgressionTemplate,
        style: VoicingStyle,
        order: KeyOrder,
        seed: Long?,
    ): Progression {
        val keys = keysFor(order, seed)
        val events = mutableListOf<ChordEvent>()
        val used = mutableListOf<SpelledPitchClass>()

        for (tonic in keys) {
            // A key whose numerals cannot be written — the ones needing a triple accidental —
            // is skipped rather than respelled. 04_HARMONY_DOMAIN_ENGINE.md §6 refuses to invent
            // a spelling, and a drill silently transposed into a different key is worse than a
            // drill that is eleven keys long.
            val placed = generate(template, KeyContext(tonic, template.mode), style)
            if (placed is SpellingResult.Spelled) {
                events += placed.value.events
                used += tonic
            }
        }

        require(events.isNotEmpty()) { "${template.title} could not be written in any key" }
        return Progression(
            id = "${template.id}.all_keys.${order.name.lowercase()}",
            title = "${template.title} through ${used.size} keys",
            key = KeyContext(used.first(), template.mode),
            events = events,
            source = ProgressionSource.GENERATED,
            loop = false,
        )
    }

    private fun keysFor(order: KeyOrder, seed: Long?): List<SpelledPitchClass> = when (order) {
        KeyOrder.CYCLE_OF_FOURTHS -> CYCLE_OF_FOURTHS
        KeyOrder.CHROMATIC -> CHROMATIC
        KeyOrder.SEEDED_SHUFFLE -> {
            val random: RandomSource = randomFactory.create(seed ?: 0L)
            random.shuffled(CYCLE_OF_FOURTHS)
        }
    }

    /**
     * Turns one chord into one event.
     *
     * The style decides the policy, and an unbuildable family falls back to the plain chord
     * rather than dropping the chord: a triad in a shell drill is still a chord to play, and
     * silently losing it from the progression would be worse than accepting it as it is.
     */
    private fun eventFor(
        id: String,
        chord: ChordSpec,
        function: FunctionalChord?,
        style: VoicingStyle,
        beats: Int,
    ): ChordEvent {
        val recipe = style.family?.let { VoicingFamilies.recipe(it, chord) }
        if (recipe != null) {
            return ChordEvent(
                id = id,
                chord = recipe.chord,
                policy = recipe.policy,
                displaySymbol = chord.symbol,
                function = function,
                durationBeats = beats,
                voicingFamily = recipe.family,
                instruction = recipe.instruction,
            )
        }

        val droppable = droppableFifth(chord)
        val policy = VoicingPolicy(
            requiredDegrees = chord.requiredDegrees - droppable,
            optionalDegrees = chord.optionalDegrees + droppable,
            allowedOmissions = droppable,
            bassRequirement = if (style == VoicingStyle.ROOT_POSITION) {
                BassRequirement.RootInBass
            } else {
                BassRequirement.Unconstrained
            },
        )
        return ChordEvent(
            id = id,
            chord = chord,
            policy = policy,
            displaySymbol = chord.symbol,
            function = function,
            durationBeats = beats,
            instruction = if (style == VoicingStyle.ROOT_POSITION) "Root position" else null,
        )
    }

    /**
     * The fifth, when this chord is one a player may leave it out of.
     *
     * `DegreeRole.FIFTH` calls the fifth "usually the first tone a jazz voicing drops", and a
     * progression run that rejected a `Dm7` played as D-F-C would be arguing with every jazz
     * pianist alive. Two limits keep that from becoming a licence:
     *
     * - only a *perfect* fifth. The b5 of a `mi7b5` and the #5 of an altered dominant are what
     *   those chords are; dropping one is playing a different chord.
     * - only from a chord that has a seventh or a sixth. A bare triad without its fifth is not
     *   a voicing of the triad, it is two notes.
     */
    private fun droppableFifth(chord: ChordSpec): Set<ChordDegree> {
        val hasSeventhOrSixth = chord.degrees.any { it.number == SEVENTH_NUMBER || it.number == SIXTH_NUMBER }
        val perfectFifth = chord.degrees.firstOrNull { it == ChordDegree.FIFTH }
        return if (hasSeventhOrSixth && perfectFifth != null) setOf(perfectFifth) else emptySet()
    }

    public companion object {
        private const val SIXTH_NUMBER = 6
        private const val SEVENTH_NUMBER = 7

        private fun key(name: String): SpelledPitchClass =
            requireNotNull(SpelledPitchClass.parseOrNull(name)) { "Bad key name: $name" }

        /**
         * The twelve keys, descending in fourths from C.
         *
         * Spelled the way a chart would write them — Gb rather than F#, Db rather than C# — so
         * that the numerals above them stay writable.
         */
        public val CYCLE_OF_FOURTHS: List<SpelledPitchClass> = listOf(
            "C", "F", "Bb", "Eb", "Ab", "Db", "Gb", "B", "E", "A", "D", "G",
        ).map(::key)

        public val CHROMATIC: List<SpelledPitchClass> = listOf(
            "C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B",
        ).map(::key)
    }
}
