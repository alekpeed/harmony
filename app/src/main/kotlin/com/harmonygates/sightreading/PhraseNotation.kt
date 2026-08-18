package com.harmonygates.sightreading

import com.harmonygates.core.designsystem.notation.NotationEvent
import com.harmonygates.core.designsystem.notation.NoteHead
import com.harmonygates.core.designsystem.notation.StaffSystem
import com.harmonygates.core.music.score.Clef
import com.harmonygates.core.music.score.RationalBeat
import com.harmonygates.core.music.score.ScoreEvent
import com.harmonygates.core.music.score.ScorePhrase
import com.harmonygates.core.music.score.StaffPlacement

/**
 * Turns a generated phrase into something the staff renderer can draw.
 *
 * `core:designsystem` deliberately does not depend on `core:music`, so neither side can convert
 * the other's model and the join has to live here in the app. That is the same boundary
 * `ArtworkScreen` and the chord screens work across, and it is why this file is a mapper rather
 * than a method on either type.
 *
 * Horizontal position is proportional to musical time, not to the number of events: a half note
 * takes twice the width of a quarter, which is what makes a bar look like the rhythm it is.
 */
internal fun ScorePhrase.toStaffSystem(progress: Float = 0f): StaffSystem {
    val total = events.maxOfOrNull { it.end.toDouble }?.takeIf { it > 0.0 } ?: 1.0
    val playedUpTo = total * progress.coerceIn(0f, 1f)

    val notation = events.sortedBy { it.onset.toDouble }.map { event ->
        val start = event.onset.toDouble / total
        val width = (event.duration.toDouble / total).toFloat()
        val clef = event.clefOf()

        NotationEvent(
            position = start.toFloat(),
            width = width,
            heads = event.pitches.map { pitch ->
                NoteHead(
                    steps = StaffPlacement.stepsFromMiddleLine(pitch, clef),
                    accidental = pitch.pitchClass.accidental.symbol.ifBlank { null },
                    // Anything shorter than a half note is a filled head, which is the whole of
                    // note-value notation this renderer draws.
                    filled = event.duration.toDouble < RationalBeat.HALF.toDouble,
                    stemUp = StaffPlacement.stepsFromMiddleLine(pitch, clef) < 0,
                )
            },
            isRest = event is ScoreEvent.Rest,
            chordSymbol = (event as? ScoreEvent.Chord)?.symbol?.symbol,
            // The cursor: the event the clock is inside right now.
            isCurrent = progress > 0f &&
                event.onset.toDouble <= playedUpTo &&
                event.end.toDouble > playedUpTo,
        )
    }

    // Barlines as fractions of the width, so they land wherever the meter actually falls rather
    // than at even intervals.
    val bars = measures.drop(1).map { (it.start.toDouble / total).toFloat() }

    return StaffSystem(
        clefGlyph = TREBLE_GLYPH,
        keySignatureAccidentals = emptyList(),
        timeSignature = meter.beats to meter.beatUnit,
        events = notation,
        barlines = bars,
    )
}

private fun ScoreEvent.clefOf(): Clef = when (this) {
    is ScoreEvent.Note -> clef
    is ScoreEvent.Chord -> clef
    is ScoreEvent.Rest -> clef
}

private const val TREBLE_GLYPH = "𝄞"
