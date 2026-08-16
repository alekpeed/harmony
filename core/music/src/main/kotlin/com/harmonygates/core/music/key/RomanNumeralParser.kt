package com.harmonygates.core.music.key

import com.harmonygates.core.music.chord.ChordFormula
import com.harmonygates.core.music.chord.ChordFormulas
import com.harmonygates.core.music.pitch.ScaleDegree

/**
 * Reads a roman numeral written the way a chart writes it.
 *
 * 21_CONTENT_AUTHORING_GUIDE.md §1: content should be data wherever existing domain types can
 * express it. A progression *is* expressible — it is a list of functions — but only if an author
 * can write one down. Without this, the mandatory vocabulary of Region 12 (the blues, rhythm
 * changes, turnaround variants) would have to be added to `Functions` as Kotlin, which makes
 * every new tune a code change.
 *
 * The grammar is the notation, not an invention:
 *
 * ```
 *   ii7        bII7       #ivø7      V7/ii       I7       vi7      Imaj7      #i°7
 *   ^accidental ^numeral   ^suffix    ^secondary target
 * ```
 *
 * Case carries meaning, as it does on a chart: `ii7` is a minor seventh and `II7` is a secondary
 * dominant, and the difference between them is the only thing the capitals say. It matters just
 * for the figures that are ambiguous on their own — a bare numeral and the plain `7`, `6`, `9`,
 * `11`, `13`. An explicit suffix overrules it, so `IIm7` and `ii7` are the same chord.
 */
public object RomanNumeralParser {

    /** Parses one numeral, or returns null. */
    public fun parseOrNull(text: String): FunctionalChord? = parse(text).getOrNull()

    /** Parses one numeral, throwing on anything unreadable. For content already validated. */
    public fun parseOrThrow(text: String): FunctionalChord =
        parse(text).getOrNull() ?: error("Unreadable roman numeral: $text")

    /** Parses one numeral, reporting why when it cannot. */
    public fun parse(text: String): Result<FunctionalChord> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return failure(text, "empty")

        // A secondary target is split off first: `V7/ii` is a `V7` read in the key of `ii`, and
        // leaving the slash in would make the suffix unreadable. But the slash in `I6/9` is part
        // of the chord quality, so a slash only separates a target when what follows one is a
        // numeral — the same distinction the chord parser draws for a slash bass.
        val slash = trimmed.lastIndexOf('/')
        val tail = if (slash >= 0) trimmed.substring(slash + 1) else null
        val separatesTarget = tail != null && readNumeral(stripAccidentals(tail).second) != null
        val head = if (separatesTarget) trimmed.substring(0, slash) else trimmed
        val targetText = if (separatesTarget) tail else null

        val target = targetText?.let { targetPart ->
            val parsedTarget = readNumeral(stripAccidentals(targetPart).second)
                ?: return failure(text, "'$targetPart' is not a scale degree")
            parsedTarget.first
        }

        val (accidental, afterAccidental) = stripAccidentals(head)
        val numeral = readNumeral(afterAccidental)
            ?: return failure(text, "does not start with a roman numeral")
        val (degree, suffixText) = numeral

        val formula = formulaFor(suffixText, upperCase = afterAccidental.first().isUpperCase())
            ?: return failure(text, "'$suffixText' is not a chord quality")

        return Result.success(
            FunctionalChord(
                romanNumeral = RomanNumeral(ScaleDegree(degree), accidental, formula.id),
                secondaryTarget = target?.let { ScaleDegree(it) },
            ),
        )
    }

    /**
     * Parses a whole progression.
     *
     * All-or-nothing, and the failure names every numeral that could not be read rather than
     * only the first. An author fixing a progression should get the whole list back at once.
     */
    public fun parseProgression(numerals: List<String>): Result<List<FunctionalChord>> {
        val parsed = numerals.map { it to parse(it) }
        val failures = parsed.mapNotNull { (_, result) -> result.exceptionOrNull()?.message }
        return if (failures.isEmpty()) {
            Result.success(parsed.map { it.second.getOrThrow() })
        } else {
            Result.failure(IllegalArgumentException(failures.joinToString("; ")))
        }
    }

    private fun stripAccidentals(text: String): Pair<Int, String> {
        var index = 0
        var alteration = 0
        while (index < text.length) {
            when (text[index]) {
                'b', '♭' -> alteration--
                '#', '♯' -> alteration++
                else -> return alteration to text.substring(index)
            }
            index++
        }
        return alteration to ""
    }

    /**
     * Reads the numeral itself and returns the degree with whatever followed it.
     *
     * Longest first, so `III` is not read as `I` followed by an unreadable `II`.
     */
    private fun readNumeral(text: String): Pair<Int, String>? {
        val upper = text.uppercase()
        return NUMERALS.firstOrNull { (numeral, _) -> upper.startsWith(numeral) }
            ?.let { (numeral, degree) -> degree to text.substring(numeral.length) }
    }

    /**
     * The sonority, from the suffix.
     *
     * Two of these are written on the numeral rather than after the root, so they are handled
     * before the ordinary chord aliases: `ø7` for a half-diminished seventh and `°7` for a
     * fully diminished one. Everything else reuses the chord vocabulary's own aliases, which is
     * what stops this from becoming a second, disagreeing list of chord qualities.
     */
    private fun formulaFor(suffix: String, upperCase: Boolean): ChordFormula? = when (suffix) {
        "" -> if (upperCase) ChordFormulas.MajorTriad else ChordFormulas.MinorTriad
        "ø7", "ø" -> ChordFormulas.HalfDiminishedSeventh
        "°7", "o7" -> ChordFormulas.DiminishedSeventh
        "°", "o" -> ChordFormulas.DiminishedTriad
        "7" -> if (upperCase) ChordFormulas.DominantSeventh else ChordFormulas.MinorSeventh
        "6" -> if (upperCase) ChordFormulas.MajorSixth else ChordFormulas.MinorSixth
        "9" -> if (upperCase) ChordFormulas.DominantNinth else ChordFormulas.MinorNinth
        "11" -> if (upperCase) ChordFormulas.DominantEleventh else ChordFormulas.MinorEleventh
        "13" -> if (upperCase) ChordFormulas.DominantThirteenth else ChordFormulas.MinorThirteenth
        else -> {
            // Brackets are punctuation on a chart, not information: `m(maj7)` and `mmaj7` name
            // one chord. And a lowercase numeral has already said "minor", so `i(maj7)` means
            // the minor-major seventh — which is why the `m`-prefixed alias is tried first
            // there and second everywhere else.
            val plain = suffix.replace("(", "").replace(")", "")
            val candidates = if (upperCase) listOf(plain, "m$plain") else listOf("m$plain", plain)
            candidates.firstNotNullOfOrNull { candidate ->
                ChordFormulas.all.firstOrNull { candidate in it.aliases }
            }
        }
    }

    private fun failure(text: String, reason: String): Result<FunctionalChord> =
        Result.failure(IllegalArgumentException("'$text': $reason"))

    /** Longest first: `VII` before `VI` before `V`, or `VII` would read as `V` plus rubbish. */
    private val NUMERALS = listOf(
        "VII" to 7,
        "VI" to 6,
        "IV" to 4,
        "V" to 5,
        "III" to 3,
        "II" to 2,
        "I" to 1,
    )
}
