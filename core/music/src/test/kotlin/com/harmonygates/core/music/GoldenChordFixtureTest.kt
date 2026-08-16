package com.harmonygates.core.music

import com.harmonygates.core.music.chord.ChordDegree
import com.harmonygates.core.music.parse.JazzChordParser
import com.harmonygates.core.music.parse.ParseResult
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.realize.DefaultChordRealizer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Serializable
private data class FixtureFile(
    @SerialName("cases") val cases: List<ChordFixture>,
)

@Serializable
private data class ChordFixture(
    val symbol: String,
    val expectedDegrees: List<String>,
    val expectedSpellings: List<String>,
    val expectedBass: String? = null,
)

/**
 * Checks the engine against a hand-derived fixture file.
 *
 * 14_TESTING_AND_QUALITY.md §3 asks for human-reviewed fixtures containing intentionally
 * awkward spellings, because logic that has collapsed into pitch-class arithmetic passes every
 * test written in C and fails silently everywhere else. The fixtures were written from theory
 * first; if the engine and the file disagree, the file is the one to trust.
 */
class GoldenChordFixtureTest {

    private val realizer = DefaultChordRealizer()

    private val fixtures: List<ChordFixture> by lazy {
        val stream = requireNotNull(javaClass.getResourceAsStream(FIXTURE_PATH)) {
            "Missing golden fixture resource $FIXTURE_PATH"
        }
        JSON.decodeFromString<FixtureFile>(stream.bufferedReader().readText()).cases
    }

    @Test
    fun `fixture file is substantial`() {
        assertTrue(fixtures.size >= 70, "Expected a broad fixture set, found ${fixtures.size}")
    }

    @Test
    fun `every fixture spells exactly as written`() {
        val failures = mutableListOf<String>()

        for (fixture in fixtures) {
            val parsed = JazzChordParser.parse(fixture.symbol)
            if (parsed !is ParseResult.Success) {
                failures += "${fixture.symbol}: did not parse ($parsed)"
                continue
            }
            val spec = parsed.value

            val actualDegrees = spec.degrees.map { it.symbol }
            if (actualDegrees != fixture.expectedDegrees) {
                failures += "${fixture.symbol}: degrees ${fixture.expectedDegrees} expected, got $actualDegrees"
            }

            val actualSpellings = realizer.chordTones(spec).map { it.toString() }
            if (actualSpellings != fixture.expectedSpellings) {
                failures += "${fixture.symbol}: spelled ${fixture.expectedSpellings} expected, got $actualSpellings"
            }

            val actualBass = spec.explicitBass?.toString()
            if (actualBass != fixture.expectedBass) {
                failures += "${fixture.symbol}: bass ${fixture.expectedBass} expected, got $actualBass"
            }
        }

        assertTrue(failures.isEmpty(), "Golden fixture mismatches:\n${failures.joinToString("\n")}")
    }

    @Test
    fun `fixture degrees round trip through the degree parser`() {
        for (fixture in fixtures) {
            for (symbol in fixture.expectedDegrees) {
                val degree = ChordDegree.parseOrNull(symbol)
                assertNotNull(degree, "Fixture ${fixture.symbol} uses unreadable degree '$symbol'")
                assertEquals(symbol, degree.symbol, "Degree symbol did not round trip")
            }
        }
    }

    @Test
    fun `fixture spellings sound as the degrees require`() {
        for (fixture in fixtures) {
            val spec = requireNotNull(JazzChordParser.parse(fixture.symbol).getOrNull())
            val rootPitchClass = spec.root.pitchClass.value
            for ((index, degreeSymbol) in fixture.expectedDegrees.withIndex()) {
                val degree = requireNotNull(ChordDegree.parseOrNull(degreeSymbol))
                val spelling = requireNotNull(SpelledPitchClass.parseOrNull(fixture.expectedSpellings[index]))
                assertEquals(
                    (rootPitchClass + degree.semitonesFromRoot).mod(12),
                    spelling.pitchClass.value,
                    "${fixture.symbol}: $degreeSymbol should sound as ${fixture.expectedSpellings[index]}",
                )
            }
        }
    }

    private companion object {
        const val FIXTURE_PATH = "/fixtures/chord_spellings.json"

        val JSON = Json { ignoreUnknownKeys = true }
    }
}
