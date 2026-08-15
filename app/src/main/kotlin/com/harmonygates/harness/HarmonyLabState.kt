package com.harmonygates.harness

import com.harmonygates.core.music.chord.ChordDegree
import com.harmonygates.core.music.chord.ChordSpec
import com.harmonygates.core.music.parse.ChordParser
import com.harmonygates.core.music.parse.JazzChordParser
import com.harmonygates.core.music.parse.ParseResult
import com.harmonygates.core.music.pitch.SpellingResult
import com.harmonygates.core.music.realize.ChordRealizer
import com.harmonygates.core.music.realize.DefaultChordRealizer
import com.harmonygates.core.music.voicing.BassRequirement
import com.harmonygates.core.music.voicing.VoicingPolicy

/**
 * What the Phase 1 harness screen shows.
 *
 * The screen is a window onto `core:music` and nothing more — 15_IMPLEMENTATION_PHASES.md
 * allows "a test harness" here and forbids campaign UI. It exists so the domain can be
 * exercised on a real tablet before MIDI arrives in Phase 2.
 */
data class HarmonyLabState(
    val input: String = "Cmaj7",
    val chordSymbol: String? = null,
    val degrees: List<String> = emptyList(),
    val tones: List<String> = emptyList(),
    val voicing: List<String> = emptyList(),
    val bassNote: String? = null,
    val errorMessage: String? = null,
) {
    val isValid: Boolean get() = errorMessage == null && chordSymbol != null
}

/** Everything the screen can ask for. One sealed type rather than a bag of callbacks. */
sealed interface HarmonyLabIntent {
    data class SymbolChanged(val text: String) : HarmonyLabIntent
}

/**
 * Maps a typed chord symbol onto screen state.
 *
 * Deliberately a plain object with no Android types: the rule in AGENTS.md is that UI code must
 * never decide whether a chord is musically correct, and the easiest way to honour that is for
 * every musical answer on this screen to come from the domain layer.
 */
class HarmonyLabAnalyzer(
    private val parser: ChordParser = JazzChordParser,
    private val realizer: ChordRealizer = DefaultChordRealizer(),
) {
    fun analyze(input: String): HarmonyLabState {
        if (input.isBlank()) {
            return HarmonyLabState(input = input, errorMessage = "Type a chord symbol, e.g. Cmaj7")
        }

        return when (val parsed = parser.parse(input)) {
            is ParseResult.Failure -> HarmonyLabState(input = input, errorMessage = parsed.message)
            is ParseResult.Success -> {
                val spec = parsed.value
                // A symbol can parse cleanly and still name a chord standard notation cannot
                // write — Cbdim7 would need a B triple-flat. Ask before spelling, because the
                // honest answer is a message, not an enharmonic substitution.
                val spelled = realizer.trySpell(spec)
                if (spelled is SpellingResult.Overflow) {
                    return HarmonyLabState(input = input, errorMessage = UNWRITABLE_MESSAGE)
                }
                val voicing = realizer.generateVoicings(spec, policyFor(spec)).firstOrNull()
                HarmonyLabState(
                    input = input,
                    chordSymbol = spec.symbol,
                    degrees = spec.degrees.map(ChordDegree::symbol),
                    tones = realizer.chordTones(spec).map { it.toString() },
                    voicing = voicing?.spelledPitches?.map { it.toString() }.orEmpty(),
                    bassNote = spec.explicitBass?.toString(),
                    errorMessage = if (voicing == null) NO_VOICING_MESSAGE else null,
                )
            }
        }
    }

    /**
     * A plain rendering in a comfortable two-hand register.
     *
     * Empty required degrees means "use the chord's own required tones", so the harness shows
     * what the chord itself asks for rather than imposing an exercise's opinion. A slash chord
     * leaves the bass unconstrained so the symbol's own bass instruction is the one that
     * applies; everything else is shown in root position because that is the clearest reading.
     */
    private fun policyFor(spec: ChordSpec) = VoicingPolicy(
        requiredDegrees = emptySet(),
        bassRequirement = if (spec.explicitBass == null) {
            BassRequirement.RootInBass
        } else {
            BassRequirement.Unconstrained
        },
        pitchRange = 48..84,
    )

    private companion object {
        const val UNWRITABLE_MESSAGE =
            "Standard notation cannot write this chord from that root. Try an enharmonic root."

        const val NO_VOICING_MESSAGE =
            "No voicing of this chord fits the harness register."
    }
}
