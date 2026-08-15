package com.harmonygates.exercise

import com.harmonygates.core.music.performance.PerformanceError

/**
 * Puts a musical diagnosis into words.
 *
 * The domain deliberately produces structured errors rather than sentences
 * (06_PERFORMANCE_EVALUATION_AND_SCORING.md §10: "A numerical score can exist... but it must
 * not obscure the musical diagnosis"), so wording lives here, next to the screen that shows it.
 *
 * Every string names the musical fact. "You are missing the seventh" teaches something; "wrong
 * notes" does not.
 */
fun describe(error: PerformanceError): String = when (error) {
    is PerformanceError.WrongRoot ->
        if (error.played == null) {
            "That chord has no ${error.expected} in it — check the root."
        } else {
            "You played a ${error.played} chord. This one is built on ${error.expected}."
        }

    is PerformanceError.WrongQuality ->
        "The ${error.expectedDegree.symbol} makes this chord what it is; you played " +
            "${error.playedDegree?.symbol ?: "something else"}."

    is PerformanceError.MissingDegree -> {
        val degree = error.tone.degree
        if (degree == null) {
            "Missing ${error.tone.pitchClass}."
        } else {
            "Missing the ${ordinal(degree.number)}, ${error.tone.pitchClass}."
        }
    }

    is PerformanceError.WrongAlteration ->
        "This chord wants ${error.expected.symbol}, not ${error.played.symbol}."

    is PerformanceError.WrongBass ->
        "The lowest note should be ${error.expected}."

    is PerformanceError.WrongTopNote ->
        "The top note is not the one this exercise asked for."

    is PerformanceError.ExtraTone ->
        error.degree?.let { "The ${it.symbol} does not belong in this chord." }
            ?: "There is a note in there that is not part of the chord."

    is PerformanceError.RegisterViolation ->
        "Play it between MIDI ${error.allowedRange.first} and ${error.allowedRange.last}."

    is PerformanceError.OnsetSpreadViolation ->
        "The notes were ${error.spreadMillis} ms apart. This exercise wants them within " +
            "${error.allowedMillis} ms."

    is PerformanceError.RhythmViolation ->
        "That landed ${error.errorMillis} ms from the beat."

    PerformanceError.NoNotesPlayed -> "Nothing was played."
}

private fun ordinal(number: Int): String = when (number) {
    1 -> "root"
    2 -> "second"
    3 -> "third"
    4 -> "fourth"
    5 -> "fifth"
    6 -> "sixth"
    7 -> "seventh"
    9 -> "ninth"
    11 -> "eleventh"
    13 -> "thirteenth"
    else -> "${number}th"
}
