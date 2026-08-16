package com.harmonygates.core.music.parse

import com.harmonygates.core.music.chord.ChordDegree
import com.harmonygates.core.music.chord.ChordFormula
import com.harmonygates.core.music.chord.ChordFormulas
import com.harmonygates.core.music.chord.ChordSpec
import com.harmonygates.core.music.chord.DegreeAlteration
import com.harmonygates.core.music.pitch.Accidental
import com.harmonygates.core.music.pitch.LetterName
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.spelling.AccidentalPolicy

/** Options that influence parsing. Deliberately small; the symbol itself carries the intent. */
public data class ParseContext(
    val accidentalPolicy: AccidentalPolicy = AccidentalPolicy.FROM_KEY_SIGNATURE,
    /** Signed key signature, negative for flats. Only used for diagnostics today. */
    val keySignatureAccidentals: Int = 0,
)

/** Why a chord symbol could not be read. */
public enum class ParseErrorReason {
    EMPTY_INPUT,
    UNKNOWN_ROOT,
    UNKNOWN_QUALITY,
    UNKNOWN_MODIFIER,
    UNKNOWN_BASS,
    TRIPLE_ACCIDENTAL,
}

/** Outcome of parsing. Failures carry a position so a UI can underline the offending text. */
public sealed interface ParseResult<out T> {
    public data class Success<T>(val value: T) : ParseResult<T>

    public data class Failure(
        val input: String,
        val position: Int,
        val reason: ParseErrorReason,
        val message: String,
    ) : ParseResult<Nothing>

    public fun getOrNull(): T? = (this as? Success)?.value
}

/** Reads jazz chord symbols into [ChordSpec]. */
public interface ChordParser {
    public fun parse(text: String, context: ParseContext = ParseContext()): ParseResult<ChordSpec>
}

/**
 * The production chord parser.
 *
 * Covers every symbol listed in 04_HARMONY_DOMAIN_ENGINE.md §5 plus the common typed
 * variants of each. Output is always a canonical [ChordSpec]; the preferred way of *writing*
 * a symbol is a separate display concern, so `CΔ7` and `Cmaj7` parse to the same value.
 */
public object JazzChordParser : ChordParser {

    override fun parse(text: String, context: ParseContext): ParseResult<ChordSpec> {
        val original = text.trim()
        if (original.isEmpty()) {
            return ParseResult.Failure(text, 0, ParseErrorReason.EMPTY_INPUT, "Chord symbol is empty")
        }

        val normalised = normalise(original)
        val (body, bassText) = splitBass(normalised)

        val bass = bassText?.let {
            SpelledPitchClass.parseOrNull(it)
                ?: return ParseResult.Failure(
                    original,
                    normalised.length - it.length,
                    ParseErrorReason.UNKNOWN_BASS,
                    "'$it' is not a note name",
                )
        }

        val rootLength = rootLength(body)
        if (rootLength == 0) {
            return ParseResult.Failure(
                original,
                0,
                ParseErrorReason.UNKNOWN_ROOT,
                "'${body.take(1)}' is not a note name",
            )
        }
        val root = SpelledPitchClass.parseOrNull(body.take(rootLength))
            ?: return ParseResult.Failure(
                original,
                0,
                ParseErrorReason.TRIPLE_ACCIDENTAL,
                "'${body.take(rootLength)}' needs an accidental this notation cannot write",
            )

        val afterRoot = normaliseQualityCasing(body.substring(rootLength))
        val formula = matchFormula(afterRoot)
            ?: return ParseResult.Failure(
                original,
                rootLength,
                ParseErrorReason.UNKNOWN_QUALITY,
                "'$afterRoot' is not a chord quality this app knows",
            )
        val afterQuality = afterRoot.substring(formula.matchedAlias.length)

        val modifiers = when (val parsed = parseModifiers(afterQuality)) {
            is ModifierResult.Parsed -> parsed
            is ModifierResult.Unknown -> return ParseResult.Failure(
                original,
                rootLength + formula.matchedAlias.length + parsed.offset,
                ParseErrorReason.UNKNOWN_MODIFIER,
                "'${parsed.text}' is not a chord modifier this app knows",
            )
        }

        return ParseResult.Success(
            ChordSpec(
                root = root,
                formulaId = formula.formula.id,
                alterations = modifiers.alterations,
                additions = modifiers.additions,
                omissions = modifiers.omissions,
                explicitBass = bass,
            ),
        )
    }

    // --- Normalisation -----------------------------------------------------------------

    /**
     * Folds the many ways a jazz symbol gets typed into one alphabet.
     *
     * Case is *not* folded: `M7` and `m7` are different chords, and losing that distinction
     * here would be a silent theory error rather than a parse error.
     */
    private fun normalise(text: String): String {
        var result = text.replace(" ", "").replace("(", "").replace(")", "")
        for ((from, to) in UNICODE_REPLACEMENTS) {
            result = result.replace(from, to)
        }
        return result
    }

    /** Accepts `CMaj7` and `CMin7` without letting `M7` collide with `m7`. */
    private fun normaliseQualityCasing(text: String): String {
        for ((from, to) in CASE_REPLACEMENTS) {
            if (text.startsWith(from)) return to + text.substring(from.length)
        }
        return text
    }

    /**
     * Splits a trailing slash bass.
     *
     * The slash in `C6/9` is part of the quality, so a slash only separates a bass when what
     * follows it actually reads as a note name.
     */
    private fun splitBass(text: String): Pair<String, String?> {
        val slash = text.lastIndexOf('/')
        if (slash <= 0 || slash == text.length - 1) return text to null
        val candidate = text.substring(slash + 1)
        val looksLikeNote = candidate.isNotEmpty() && LetterName.parse(candidate[0]) != null
        return if (looksLikeNote) text.substring(0, slash) to candidate else text to null
    }

    private fun rootLength(text: String): Int {
        if (text.isEmpty() || LetterName.parse(text[0]) == null) return 0
        var length = 1
        while (length < text.length && text[length] in ROOT_ACCIDENTAL_CHARS) length++
        return length
    }

    // --- Quality -----------------------------------------------------------------------

    private data class FormulaMatch(val formula: ChordFormula, val matchedAlias: String)

    /**
     * Longest alias wins, so `m7b5` is a half-diminished seventh rather than a minor seventh
     * followed by an unreadable `b5`.
     */
    private fun matchFormula(text: String): FormulaMatch? =
        ChordFormulas.aliasesByLengthDescending
            .firstOrNull { (alias, _) -> text.startsWith(alias) }
            ?.let { (alias, formula) -> FormulaMatch(formula, alias) }

    // --- Modifiers ---------------------------------------------------------------------

    private sealed interface ModifierResult {
        data class Parsed(
            val alterations: Set<DegreeAlteration>,
            val additions: Set<ChordDegree>,
            val omissions: Set<ChordDegree>,
        ) : ModifierResult

        data class Unknown(val offset: Int, val text: String) : ModifierResult
    }

    private fun parseModifiers(text: String): ModifierResult {
        val alterations = mutableSetOf<DegreeAlteration>()
        val additions = mutableSetOf<ChordDegree>()
        val omissions = mutableSetOf<ChordDegree>()
        var index = 0

        while (index < text.length) {
            val addPrefix = ADD_PREFIXES.firstOrNull { text.startsWith(it, index) }
            val omitPrefix = OMIT_PREFIXES.firstOrNull { text.startsWith(it, index) }
            val prefixLength = addPrefix?.length ?: omitPrefix?.length ?: 0
            val degreeStart = index + prefixLength

            val degree = readDegree(text, degreeStart)
                ?: return ModifierResult.Unknown(index, text.substring(index))

            when {
                addPrefix != null -> additions += degree.value
                omitPrefix != null -> omissions += degree.value
                else -> alterations += DegreeAlteration.of(degree.value)
            }
            index = degree.endExclusive
        }

        return ModifierResult.Parsed(alterations, additions, omissions)
    }

    private data class DegreeToken(val value: ChordDegree, val endExclusive: Int)

    /** Reads `b9`, `#11`, `13`, `bb7` starting at [start]. */
    private fun readDegree(text: String, start: Int): DegreeToken? {
        var index = start
        var alteration = 0
        while (index < text.length && text[index] in ALTERATION_CHARS) {
            alteration += if (text[index] == '#') 1 else -1
            index++
        }
        val digitsStart = index
        while (index < text.length && text[index].isDigit()) index++
        if (index == digitsStart) return null
        val number = text.substring(digitsStart, index).toIntOrNull() ?: return null
        if (alteration !in -2..2) return null
        val degree = runCatching { ChordDegree(number, alteration) }.getOrNull() ?: return null
        return DegreeToken(degree, index)
    }

    private val UNICODE_REPLACEMENTS = listOf(
        "Δ7" to "maj7",
        "△7" to "maj7",
        "Δ" to "maj7",
        "△" to "maj7",
        "ø7" to "m7b5",
        "ø" to "m7b5",
        "Ø7" to "m7b5",
        "Ø" to "m7b5",
        "°7" to "dim7",
        "°" to "dim",
        "º7" to "dim7",
        "º" to "dim",
        "♯" to "#",
        "♭" to "b",
        "𝄪" to "x",
        "𝄫" to "bb",
        "−" to "-",
        "–" to "-",
    )

    private val CASE_REPLACEMENTS = listOf(
        "MAJ" to "maj",
        "Maj" to "maj",
        "MIN" to "min",
        "Min" to "min",
        "DIM" to "dim",
        "Dim" to "dim",
        "AUG" to "aug",
        "Aug" to "aug",
        "SUS" to "sus",
        "Sus" to "sus",
        "ALT" to "alt",
        "Alt" to "alt",
        "Add" to "add",
        "ADD" to "add",
    )

    private val ADD_PREFIXES = listOf("add")
    private val OMIT_PREFIXES = listOf("omit", "no")
    private val ROOT_ACCIDENTAL_CHARS = setOf('#', 'b', 'x')
    private val ALTERATION_CHARS = setOf('#', 'b')
}

/** Convenience wrapper for tests and fixtures where a malformed symbol is a programming error. */
public fun ChordParser.parseOrThrow(text: String, context: ParseContext = ParseContext()): ChordSpec =
    when (val result = parse(text, context)) {
        is ParseResult.Success -> result.value
        is ParseResult.Failure -> error("Could not parse chord symbol '$text': ${result.message}")
    }

/** Builds a chord spec from a root and formula without going through text. */
public fun chordOf(
    root: String,
    formula: ChordFormula,
    alterations: Set<DegreeAlteration> = emptySet(),
    additions: Set<ChordDegree> = emptySet(),
    omissions: Set<ChordDegree> = emptySet(),
    bass: String? = null,
): ChordSpec = ChordSpec(
    root = requireNotNull(SpelledPitchClass.parseOrNull(root)) { "Not a note name: $root" },
    formulaId = formula.id,
    alterations = alterations,
    additions = additions,
    omissions = omissions,
    explicitBass = bass?.let { requireNotNull(SpelledPitchClass.parseOrNull(it)) { "Not a note name: $it" } },
)

/** All twelve roots spelled the way jazz lead sheets normally write them. */
public val StandardRoots: List<SpelledPitchClass> = listOf(
    SpelledPitchClass(LetterName.C),
    SpelledPitchClass(LetterName.D, Accidental.FLAT),
    SpelledPitchClass(LetterName.D),
    SpelledPitchClass(LetterName.E, Accidental.FLAT),
    SpelledPitchClass(LetterName.E),
    SpelledPitchClass(LetterName.F),
    SpelledPitchClass(LetterName.G, Accidental.FLAT),
    SpelledPitchClass(LetterName.G),
    SpelledPitchClass(LetterName.A, Accidental.FLAT),
    SpelledPitchClass(LetterName.A),
    SpelledPitchClass(LetterName.B, Accidental.FLAT),
    SpelledPitchClass(LetterName.B),
)
