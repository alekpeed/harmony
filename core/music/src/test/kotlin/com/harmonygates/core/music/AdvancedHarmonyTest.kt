package com.harmonygates.core.music

import com.harmonygates.core.music.chord.ChordDegree
import com.harmonygates.core.music.chord.ChordFormulas
import com.harmonygates.core.music.chord.ChordSpec
import com.harmonygates.core.music.exercise.ExerciseRequirement
import com.harmonygates.core.music.harmony.AlteredDominants
import com.harmonygates.core.music.harmony.DiminishedFunction
import com.harmonygates.core.music.harmony.DiminishedSystems
import com.harmonygates.core.music.harmony.DominantAlteration
import com.harmonygates.core.music.harmony.SubstitutionKind
import com.harmonygates.core.music.harmony.Substitutions
import com.harmonygates.core.music.parse.JazzChordParser
import com.harmonygates.core.music.parse.StandardRoots
import com.harmonygates.core.music.parse.parseOrThrow
import com.harmonygates.core.music.performance.DefaultPerformanceEvaluator
import com.harmonygates.core.music.performance.EvaluationResult
import com.harmonygates.core.music.performance.PerformanceError
import com.harmonygates.core.music.performance.Verdict
import com.harmonygates.core.music.pitch.SpelledInterval
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.realize.DefaultChordRealizer
import com.harmonygates.core.music.transform.Transposition
import com.harmonygates.core.music.voicing.VoicingFamilies
import com.harmonygates.core.music.voicing.VoicingFamily
import com.harmonygates.core.music.voicing.VoicingPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Region 9 to 12: the harmony a player meets after the seventh chords.
 *
 * 15_IMPLEMENTATION_PHASES.md asks Phase 11 for tests that "cover all formulas and
 * representative contexts", and the two halves of that sentence are tested differently. The
 * formulas are covered exhaustively by an interval table written out by hand below — an
 * independent statement of what each symbol means, so a typo in `ChordFormulas` fails here
 * rather than being asserted against itself. The contexts are covered by example, because a
 * substitution or a diminished chord is only itself in a place: `Db7` is a chord, and
 * `Dm7 Db7 Cmaj7` is a tritone substitution.
 */
class AdvancedHarmonyTest {

    private val realizer = DefaultChordRealizer()
    private val evaluator = DefaultPerformanceEvaluator()
    private val diminished = DiminishedSystems()
    private val substitutions = Substitutions()

    private fun chord(symbol: String): ChordSpec = JazzChordParser.parseOrThrow(symbol)

    // --- Every formula in the vocabulary ---------------------------------------------------

    /**
     * What each chord symbol sounds, as semitones above the root.
     *
     * Written out rather than derived, deliberately. Deriving it from `ChordDegree` would make
     * this a test of nothing: the point is that a second, independent description of the
     * vocabulary agrees with the one the engine uses.
     */
    private val soundingIntervals: Map<String, Set<Int>> = mapOf(
        "major_triad" to setOf(0, 4, 7),
        "minor_triad" to setOf(0, 3, 7),
        "diminished_triad" to setOf(0, 3, 6),
        "augmented_triad" to setOf(0, 4, 8),
        "sus2" to setOf(0, 2, 7),
        "sus4" to setOf(0, 5, 7),
        "major_sixth" to setOf(0, 4, 7, 9),
        "minor_sixth" to setOf(0, 3, 7, 9),
        "six_nine" to setOf(0, 2, 4, 7, 9),
        "major_seventh" to setOf(0, 4, 7, 11),
        "dominant_seventh" to setOf(0, 4, 7, 10),
        "minor_seventh" to setOf(0, 3, 7, 10),
        "minor_major_seventh" to setOf(0, 3, 7, 11),
        "half_diminished_seventh" to setOf(0, 3, 6, 10),
        // The seventh is a double-flatted seventh: nine semitones, written as a seventh.
        "diminished_seventh" to setOf(0, 3, 6, 9),
        "augmented_seventh" to setOf(0, 4, 8, 10),
        "major_seventh_sharp_five" to setOf(0, 4, 8, 11),
        "dominant_ninth" to setOf(0, 2, 4, 7, 10),
        "major_ninth" to setOf(0, 2, 4, 7, 11),
        "minor_ninth" to setOf(0, 2, 3, 7, 10),
        "dominant_eleventh" to setOf(0, 2, 4, 5, 7, 10),
        "minor_eleventh" to setOf(0, 2, 3, 5, 7, 10),
        "major_eleventh" to setOf(0, 2, 4, 5, 7, 11),
        // No natural eleventh in the written stack, though a performance may contain one.
        "dominant_thirteenth" to setOf(0, 2, 4, 7, 9, 10),
        "major_thirteenth" to setOf(0, 2, 4, 7, 9, 11),
        "minor_thirteenth" to setOf(0, 2, 3, 7, 9, 10),
        "dominant_seventh_sus4" to setOf(0, 5, 7, 10),
        "dominant_ninth_sus4" to setOf(0, 2, 5, 7, 10),
        "dominant_thirteenth_sus4" to setOf(0, 2, 5, 7, 9, 10),
        // Guide tones plus every alteration: the whole colour space the symbol permits.
        "altered_dominant" to setOf(0, 1, 3, 4, 6, 8, 10),
    )

    @Test
    fun `the interval table describes exactly the formulas that exist`() {
        assertEquals(
            ChordFormulas.all.map { it.id.value }.toSet(),
            soundingIntervals.keys,
            "A formula was added or removed without stating what it sounds",
        )
    }

    @Test
    fun `every formula sounds the intervals it names`() {
        for (formula in ChordFormulas.all) {
            val expected = soundingIntervals.getValue(formula.id.value)
            val actual = formula.canonicalDegrees.map { it.semitonesFromRoot.mod(SEMITONES) }.toSet()
            assertEquals(expected, actual, "${formula.displayName} sounds the wrong intervals")
        }
    }

    @Test
    fun `every formula sounds those intervals from every root`() {
        for (formula in ChordFormulas.all) {
            val expected = soundingIntervals.getValue(formula.id.value)
            for (root in StandardRoots) {
                val tones = realizer.chordTones(ChordSpec(root, formula.id))
                    .map { (it.pitchClass.value - root.pitchClass.value).mod(SEMITONES) }
                    .toSet()
                assertEquals(expected, tones, "$root${formula.canonicalSuffix} sounds the wrong intervals")
            }
        }
    }

    @Test
    fun `every formula names the tone that fixes its quality`() {
        // Region 9 onwards keeps asking "is this chord major or minor" of chords with six tones
        // in them. That question is answerable for every formula in the vocabulary, including
        // the suspended ones, where the answer is the suspended tone rather than a third.
        for (formula in ChordFormulas.all) {
            assertNotNull(formula.qualityDegree, "${formula.displayName} has no tone fixing its quality")
        }
    }

    @Test
    fun `a formula never permits a tone it forbids`() {
        for (formula in ChordFormulas.all) {
            val contradiction = formula.canonicalDegrees intersect formula.forbiddenDegrees
            assertTrue(contradiction.isEmpty(), "${formula.displayName} both writes and forbids $contradiction")
        }
    }

    // --- Region 10: extensions and alterations ----------------------------------------------

    @Test
    fun `an alteration is built from the dominant it alters`() {
        val altered = assertNotNull(AlteredDominants.alter(chord("C7"), DominantAlteration.FLAT_NINE))

        assertEquals("C7b9", altered.symbol)
        assertTrue(ChordDegree.FLAT_NINTH in altered.degrees)
    }

    @Test
    fun `altering something that is not a dominant is refused rather than guessed at`() {
        assertNull(AlteredDominants.alter(chord("Cmaj7"), DominantAlteration.FLAT_NINE))
        assertNull(AlteredDominants.alter(chord("Cm7"), DominantAlteration.SHARP_NINE))
        assertNull(AlteredDominants.altered(chord("Cmaj7")))
    }

    @Test
    fun `each of the four alterations produces a distinct chord`() {
        val forms = AlteredDominants.singleAlterations(chord("G7"))

        assertEquals(DominantAlteration.entries.size, forms.size)
        assertEquals(forms.size, forms.map { it.symbol }.toSet().size, "Two alterations spell the same chord")
        assertEquals(
            listOf("G7b9", "G7#9", "G7#11", "G7b13"),
            forms.map { it.symbol },
        )
    }

    @Test
    fun `alterations keep the guide tones and change only the colour`() {
        val original = chord("C7")
        val guideTones = AlteredDominants.guideTones(original)
        assertEquals(setOf(ChordDegree.THIRD, ChordDegree.FLAT_SEVENTH), guideTones)

        val everyForm = AlteredDominants.singleAlterations(original) +
            AlteredDominants.combinedAlterations(original) +
            listOfNotNull(AlteredDominants.altered(original))

        for (form in everyForm) {
            assertTrue(
                AlteredDominants.retainsGuideTones(original, form),
                "${form.symbol} lost a guide tone of ${original.symbol}",
            )
            for (root in listOf(ChordDegree.THIRD, ChordDegree.FLAT_SEVENTH)) {
                val sounded = realizer.spelledDegrees(form)[root]
                assertNotNull(sounded, "${form.symbol} does not sound its ${root.symbol}")
            }
        }
    }

    @Test
    fun `combined alterations pair a ninth with a fifth and never two ninths`() {
        val combined = AlteredDominants.combinedAlterations(chord("C7"))

        assertEquals(COMBINED_FORMS, combined.size)
        for (form in combined) {
            val ninths = form.degrees.filter { it.number == NINTH }
            assertEquals(1, ninths.size, "${form.symbol} alters the ninth twice")
            assertTrue(
                form.degrees.any { it == ChordDegree.SHARP_ELEVENTH || it == ChordDegree.FLAT_THIRTEENTH },
                "${form.symbol} has no altered fifth",
            )
        }
    }

    @Test
    fun `the alt chord permits every alteration and forbids the natural tones`() {
        val alt = assertNotNull(AlteredDominants.altered(chord("C7")))

        assertEquals("C7alt", alt.symbol)
        assertEquals(
            setOf(ChordDegree.ROOT, ChordDegree.THIRD, ChordDegree.FLAT_SEVENTH),
            alt.requiredDegrees,
        )
        // The natural fifth, ninth and thirteenth contradict the altered scale the symbol names.
        for (natural in listOf(ChordDegree.FIFTH, ChordDegree.NINTH, ChordDegree.THIRTEENTH)) {
            assertTrue(natural in alt.forbiddenDegrees, "C7alt should not permit ${natural.symbol}")
        }
    }

    @Test
    fun `the two alterations of the fifth are the ones flagged as displacing it`() {
        assertEquals(
            setOf(DominantAlteration.SHARP_ELEVEN, DominantAlteration.FLAT_THIRTEEN),
            DominantAlteration.entries.filter { it.displacesTheFifth }.toSet(),
        )
    }

    // --- Region 11: the diminished submodule -------------------------------------------------

    @Test
    fun `a diminished seventh is the same chord under all four of its names`() {
        val original = chord("Cdim7")
        val spellings = diminished.equivalentSpellings(original)

        assertTrue(spellings.isNotEmpty(), "Cdim7 should be respellable")
        for (spelling in spellings) {
            assertTrue(
                diminished.isSameChord(original, spelling),
                "${spelling.symbol} should be the same four notes as ${original.symbol}",
            )
            assertEquals(diminished.pitchClasses(original), diminished.pitchClasses(spelling))
        }
    }

    @Test
    fun `there are exactly three diminished sevenths and every root belongs to one`() {
        val families = diminished.distinctFamilies()
        assertEquals(DIMINISHED_FAMILIES, families.size)
        assertEquals(
            DIMINISHED_FAMILIES,
            families.map { diminished.pitchClasses(it) }.toSet().size,
            "Two of the three families are the same chord",
        )

        for (root in StandardRoots) {
            val spec = ChordSpec(root, ChordFormulas.DiminishedSeventh.id)
            val family = assertNotNull(diminished.familyOf(spec))
            assertTrue(
                diminished.isSameChord(spec, families[family]),
                "${spec.symbol} was filed under a family it does not belong to",
            )
        }
    }

    @Test
    fun `two diminished sevenths share a family exactly when they are the same chord`() {
        val roots = StandardRoots.map { ChordSpec(it, ChordFormulas.DiminishedSeventh.id) }

        for (first in roots) {
            for (second in roots) {
                assertEquals(
                    diminished.familyOf(first) == diminished.familyOf(second),
                    diminished.isSameChord(first, second),
                    "${first.symbol} and ${second.symbol} disagree about being the same chord",
                )
            }
        }
    }

    @Test
    fun `only a diminished seventh is treated as one`() {
        assertNull(diminished.familyOf(chord("Cdim")), "A diminished triad is a different sonority")
        assertEquals(emptyList(), diminished.equivalentSpellings(chord("Cm7b5")))
        assertNull(diminished.asDominantSubstitute(chord("C7")))
    }

    @Test
    fun `a diminished seventh is the upper structure of the dominant b9 a major third below`() {
        val substitute = assertNotNull(diminished.asDominantSubstitute(chord("Bdim7")))
        assertEquals("G7b9", substitute.symbol)

        // And back the other way: the diminished chord sits on the third of the dominant.
        val upperStructure = assertNotNull(diminished.fromDominant(chord("G7")))
        assertEquals("Bdim7", upperStructure.symbol)
        assertTrue(diminished.isSameChord(chord("Bdim7"), upperStructure))
    }

    @Test
    fun `the dominant b9 contains every note of its diminished chord`() {
        val dominant = assertNotNull(AlteredDominants.alter(chord("G7"), DominantAlteration.FLAT_NINE))
        val upperStructure = assertNotNull(diminished.fromDominant(chord("G7")))

        val dominantTones = realizer.chordTones(dominant).map { it.pitchClass }.toSet()
        val diminishedTones = diminished.pitchClasses(upperStructure)

        assertTrue(
            diminishedTones.all { it in dominantTones },
            "${upperStructure.symbol} should be ${dominant.symbol} without its root",
        )
        assertEquals(
            setOf(dominant.root.pitchClass),
            dominantTones - diminishedTones,
            "The only note the dominant adds is its root",
        )
    }

    @Test
    fun `a passing diminished chord sits between two chords a whole step apart`() {
        val passing = assertNotNull(diminished.passingBetween(chord("Cmaj7"), chord("Dm7")))

        assertEquals("C#dim7", passing.chord.symbol, "The passing chord keeps the letter it passes from")
        assertEquals(DiminishedFunction.PASSING, passing.function)
        assertNotNull(passing.relatedDominant, "A passing chord is still a dominant substitute")
    }

    @Test
    fun `chords that are not a whole step apart have nothing to pass through`() {
        assertNull(diminished.passingBetween(chord("Cmaj7"), chord("Fmaj7")))
        assertNull(diminished.passingBetween(chord("Cmaj7"), chord("Cmaj7")))
    }

    @Test
    fun `a common-tone diminished chord shares tones with the chord it decorates`() {
        val use = assertNotNull(diminished.commonToneFor(chord("C")))

        assertEquals("C#dim7", use.chord.symbol)
        assertEquals(DiminishedFunction.COMMON_TONE, use.function)

        val shared = diminished.pitchClasses(use.chord) intersect
            realizer.chordTones(chord("C")).map { it.pitchClass }.toSet()
        assertEquals(
            COMMON_TONES,
            shared.size,
            "A common-tone diminished chord holds the third and the fifth still",
        )
    }

    // --- Region 12: substitutions -------------------------------------------------------------

    @Test
    fun `a tritone substitution shares its guide tones with the dominant it replaces`() {
        val substitution = assertNotNull(substitutions.tritone(chord("G7")))

        assertEquals("Db7", substitution.replacement.symbol)
        assertEquals(SubstitutionKind.TRITONE, substitution.kind)
        assertEquals(
            GUIDE_TONES,
            substitution.sharedTones.size,
            "The third of one is the seventh of the other, both ways round",
        )
        val shared = substitution.sharedTones.map { it.pitchClass.value }.toSet()
        assertEquals(setOf(B_NATURAL, F_NATURAL), shared)
    }

    @Test
    fun `substituting twice returns the chord it started from`() {
        for (root in StandardRoots) {
            val original = ChordSpec(root, ChordFormulas.DominantSeventh.id)
            val once = assertNotNull(substitutions.tritone(original), "No substitute for ${original.symbol}")
            val twice = assertNotNull(
                substitutions.tritone(once.replacement),
                "No substitute for ${once.replacement.symbol}, the substitute for ${original.symbol}",
            )

            assertEquals(
                original.root.pitchClass,
                twice.replacement.root.pitchClass,
                "${original.symbol} substituted twice should sound like itself again",
            )
        }
    }

    @Test
    fun `only a dominant can be substituted for a dominant`() {
        assertNull(substitutions.tritone(chord("Cmaj7")))
        assertNull(substitutions.relatedTwo(chord("Cm7")))
        assertNull(substitutions.diminishedFor(chord("Cmaj7")))
    }

    @Test
    fun `the related ii turns a dominant into a two-chord approach`() {
        val substitution = assertNotNull(substitutions.relatedTwo(chord("G7")))
        assertEquals("Dm7", substitution.replacement.symbol)

        val progression = substitutions.insertBefore(
            listOf(chord("G7"), chord("Cmaj7")),
            index = 0,
            substitution = substitution,
        )
        assertEquals(
            listOf("Dm7", "G7", "Cmaj7"),
            progression.map { it.symbol },
            "Inserting must not delete the chord it approaches",
        )
    }

    @Test
    fun `a secondary dominant is the dominant of the chord it precedes`() {
        val substitution = assertNotNull(substitutions.secondaryDominant(chord("Dm7")))

        assertEquals("A7", substitution.replacement.symbol)
        assertEquals(SubstitutionKind.SECONDARY_DOMINANT, substitution.kind)
    }

    @Test
    fun `the backdoor dominant approaches a major tonic from a whole step below`() {
        val substitution = assertNotNull(substitutions.backdoor(chord("Cmaj7")))

        assertEquals("Bb7", substitution.replacement.symbol)
        assertEquals(SubstitutionKind.BACKDOOR, substitution.kind)
    }

    @Test
    fun `a substitution is recognisable only in the progression it happens in`() {
        val progression = listOf(chord("Dm7"), chord("G7"), chord("Cmaj7"))
        val substitution = assertNotNull(substitutions.tritone(progression[1]))
        val rewritten = substitutions.applyTo(progression, index = 1, substitution = substitution)

        assertEquals(listOf("Dm7", "Db7", "Cmaj7"), rewritten.map { it.symbol })
        assertEquals(progression.size, rewritten.size, "A replacement must not change the length")
    }

    @Test
    fun `rewriting outside the progression is refused rather than silently ignored`() {
        val progression = listOf(chord("G7"))
        val substitution = assertNotNull(substitutions.tritone(progression[0]))

        assertTrue(runCatching { substitutions.applyTo(progression, 3, substitution) }.isFailure)
        assertTrue(runCatching { substitutions.insertBefore(progression, -1, substitution) }.isFailure)
    }

    @Test
    fun `a dominant offers every substitution the curriculum teaches`() {
        val available = substitutions.available(chord("G7"))

        assertEquals(
            SubstitutionKind.entries.toSet(),
            available.map { it.kind }.toSet(),
            "Every kind of substitution should be reachable from a plain dominant",
        )
        for (substitution in available) {
            assertTrue(substitution.explanation.isNotBlank(), "${substitution.kind} explains nothing")
        }
    }

    // --- Region 9: quartal structures ---------------------------------------------------------

    @Test
    fun `a quartal voicing stacks fourths over the chord`() {
        val recipe = assertNotNull(VoicingFamilies.recipe(VoicingFamily.QUARTAL, chord("Dm7")))

        // D3 G3 C4 F4 — the So What shape.
        assertEquals(Verdict.CORRECT, verdictOf(recipe.chord, recipe.policy, listOf(50, 55, 60, 65)))
    }

    @Test
    fun `an inverted fourth stack is still a fourth stack`() {
        val recipe = assertNotNull(VoicingFamilies.recipe(VoicingFamily.QUARTAL, chord("Dm7")))

        // G3 C4 F4 D5 — rotated, so the root is on top. 03_JAZZ_CURRICULUM.md §11 declines to
        // call one rotation the chord and the others wrong.
        assertEquals(Verdict.CORRECT, verdictOf(recipe.chord, recipe.policy, listOf(55, 60, 65, 74)))
    }

    @Test
    fun `a quartal voicing leaves out the tone the fourth replaced`() {
        val recipe = assertNotNull(VoicingFamilies.recipe(VoicingFamily.QUARTAL, chord("Dm7")))

        // Adding the fifth, A3, makes it a different sonority: the fourth is there instead.
        val result = evaluate(recipe.chord, recipe.policy, listOf(50, 55, 57, 60, 65))

        assertTrue(!result.verdict.isCorrect, "A fourth stack with a fifth in it is not a fourth stack")
        assertTrue(
            result.semanticErrors.filterIsInstance<PerformanceError.ExtraTone>()
                .any { it.degree == ChordDegree.FIFTH },
            "The player should be told it was the fifth, not merely that something was wrong: " +
                result.semanticErrors,
        )
    }

    @Test
    fun `a chord with no seventh has no fourth stack to build`() {
        assertNull(VoicingFamilies.recipe(VoicingFamily.QUARTAL, chord("C")))
        assertNull(VoicingFamilies.recipe(VoicingFamily.QUARTAL, chord("Cdim")))
    }

    @Test
    fun `the quartal family is one the engine says it supports`() {
        assertTrue(VoicingFamily.QUARTAL in VoicingFamilies.supported)
    }

    // --- Suspended and slash chords -----------------------------------------------------------

    @Test
    fun `a suspended chord refuses the third it suspends`() {
        val sus = chord("G7sus4")

        assertTrue(ChordDegree.FOURTH in sus.degrees)
        assertTrue(ChordDegree.THIRD in sus.forbiddenDegrees, "A sounding third resolves the suspension")
        // G3 C4 D4 F4 — root, fourth, fifth, seventh.
        assertEquals(Verdict.CORRECT, chordVerdict(sus, listOf(55, 60, 62, 65)))
        // The same with B natural in it is a G7, not a G7sus4.
        assertTrue(!chordVerdict(sus, listOf(55, 59, 60, 62, 65)).isCorrect)
    }

    @Test
    fun `a slash chord names its bass whether or not the bass is a chord tone`() {
        val inversion = chord("C/E")
        assertEquals(SpelledPitchClass.E, inversion.explicitBass)
        assertEquals(
            ChordDegree.THIRD,
            realizer.degreeOf(inversion, SpelledPitchClass.E.pitchClass),
            "The E of C/E is the chord's own third",
        )

        val independentBass = chord("C/A")
        assertEquals(SpelledPitchClass.A, independentBass.explicitBass)
        assertNull(
            realizer.degreeOf(independentBass, SpelledPitchClass.A.pitchClass),
            "The A of C/A belongs to no degree of C, and guessing one would be an invention",
        )
    }

    @Test
    fun `a slash bass survives transposition as a bass`() {
        val transposed = Transposition.transpose(chord("C/E"), SpelledInterval.MAJOR_SECOND).getOrNull()

        assertEquals("D/F#", assertNotNull(transposed).symbol)
    }

    // --- Helpers --------------------------------------------------------------------------------

    private fun evaluate(
        spec: ChordSpec,
        policy: VoicingPolicy,
        notes: List<Int>,
    ): EvaluationResult = evaluator.evaluate(
        ExerciseRequirement.ChordPolicyMatch(spec, policy),
        attemptOf(notes),
    )

    private fun verdictOf(spec: ChordSpec, policy: VoicingPolicy, notes: List<Int>): Verdict =
        evaluate(spec, policy, notes).verdict

    /** "Play this chord, any voicing" — the plainest requirement there is. */
    private fun chordVerdict(spec: ChordSpec, notes: List<Int>): Verdict =
        evaluator.evaluate(
            ExerciseRequirement.PitchSet(
                pitchClasses = realizer.chordTones(spec).toSet(),
                allowExtraNotes = false,
                chord = spec,
            ),
            attemptOf(notes),
        ).verdict

    private companion object {
        const val SEMITONES = 12
        const val NINTH = 9
        const val COMBINED_FORMS = 4
        const val DIMINISHED_FAMILIES = 3
        const val COMMON_TONES = 2
        const val GUIDE_TONES = 2
        const val B_NATURAL = 11
        const val F_NATURAL = 5
    }
}
