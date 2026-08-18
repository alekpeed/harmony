package com.harmonygates.core.music.relativepitch

/**
 * The ladder, in order.
 *
 * A strictly linear chain — level N requires level N-1 mastered, full stop, across every tier —
 * because "relative pitch from the ground up" means the ground actually comes first. Interval
 * recognition (telling two pitches apart, then naming the distance) comes before scale-degree
 * recognition (placing a note in a key), which comes before chord quality (recognising several
 * of those distances stacked at once), which comes before hearing a chord's function in context.
 * Keyboard reproduction — the one thing the app asked for before this existed — is the capstone,
 * not the entry point: it only makes sense once the ear can already identify what it is being
 * asked to play back.
 *
 * Each multiple-choice tier's levels are cumulative — a level's choice list is the previous
 * level's plus what is new — so passing a level never narrows what a later one can ask about.
 * New intervals are introduced easiest-to-hear first: the octave and the unison (as different as
 * two notes get) before the major/minor third (the interval that decides major vs. minor), the
 * fourth and fifth (harmonically the plainest), and only then the major/minor second — the
 * closest, hardest-to-tell-apart interval there is, which is deliberately not where beginners
 * start.
 */
public object RelativePitchCurriculum {

    public val levels: List<RelativePitchLevel> = buildList {
        addAll(intervalLevels())
        addAll(degreeLevels())
        addAll(qualityLevels())
        add(
            RelativePitchLevel(
                id = "function.1",
                tier = RelativePitchTier.FUNCTION_HEARING,
                title = "Chord functions, in a key",
                prompt = "A tonic, then a chord from a ii-V-I. Play what you hear.",
            ),
        )
        add(
            RelativePitchLevel(
                id = "reproduce.1",
                tier = RelativePitchTier.REPRODUCE,
                title = "Play what you hear",
                prompt = "A reference note, then a chord. Play it on the keyboard.",
            ),
        )
    }

    public fun level(id: String): RelativePitchLevel? = levels.firstOrNull { it.id == id }

    /** The level immediately before [id] in the ladder, or null if it is the first. */
    public fun previous(id: String): RelativePitchLevel? {
        val index = levels.indexOfFirst { it.id == id }
        return levels.getOrNull(index - 1)
    }

    private fun intervalLevels(): List<RelativePitchLevel> {
        val step1 = listOf(IntervalClass.UNISON, IntervalClass.OCTAVE)
        val step2 = step1 + IntervalClass.PERFECT_FIFTH
        val step3 = step2 + listOf(IntervalClass.MAJOR_THIRD, IntervalClass.MINOR_THIRD)
        val step4 = step3 + IntervalClass.PERFECT_FOURTH
        val step5 = step4 + listOf(IntervalClass.MAJOR_SECOND, IntervalClass.MINOR_SECOND)
        val step6 = step5 + listOf(IntervalClass.MAJOR_SIXTH, IntervalClass.MINOR_SIXTH)
        val step7 = step6 + listOf(IntervalClass.MAJOR_SEVENTH, IntervalClass.MINOR_SEVENTH)
        val step8 = step7 + IntervalClass.TRITONE

        return listOf(
            interval("interval.1", "Same, or an octave apart", step1),
            interval("interval.2", "Adding the fifth", step2),
            interval("interval.3", "Major and minor thirds", step3),
            interval("interval.4", "The fourth", step4),
            interval("interval.5", "Major and minor seconds", step5),
            interval("interval.6", "Major and minor sixths", step6),
            interval("interval.7", "Major and minor sevenths", step7),
            interval("interval.8", "The tritone: every interval", step8),
        )
    }

    private fun interval(id: String, title: String, choices: List<IntervalClass>) = RelativePitchLevel(
        id = id,
        tier = RelativePitchTier.INTERVALS,
        title = title,
        prompt = "Two notes. Which interval?",
        intervalChoices = choices,
    )

    private fun degreeLevels(): List<RelativePitchLevel> {
        val step1 = listOf(ScaleDegree.ONE, ScaleDegree.FIVE)
        val step2 = step1 + ScaleDegree.THREE
        val step3 = ScaleDegree.entries.toList()

        return listOf(
            degree("degree.1", "Home, and the fifth", step1),
            degree("degree.2", "Adding the third", step2),
            degree("degree.3", "The full scale", step3),
        )
    }

    private fun degree(id: String, title: String, choices: List<ScaleDegree>) = RelativePitchLevel(
        id = id,
        tier = RelativePitchTier.SCALE_DEGREES,
        title = title,
        prompt = "A tonic, then a note. Which scale degree?",
        degreeChoices = choices,
    )

    private fun qualityLevels(): List<RelativePitchLevel> {
        val step1 = listOf(ChordQuality.MAJOR, ChordQuality.MINOR)
        val step2 = step1 + listOf(ChordQuality.DIMINISHED, ChordQuality.AUGMENTED)
        val step3 = step2 + listOf(
            ChordQuality.MAJOR_SEVENTH,
            ChordQuality.DOMINANT_SEVENTH,
            ChordQuality.MINOR_SEVENTH,
        )
        val step4 = step3 + listOf(ChordQuality.HALF_DIMINISHED, ChordQuality.DIMINISHED_SEVENTH)

        return listOf(
            quality("quality.1", "Major or minor", step1),
            quality("quality.2", "Diminished and augmented", step2),
            quality("quality.3", "Seventh chords", step3),
            quality("quality.4", "Half- and fully-diminished sevenths", step4),
        )
    }

    private fun quality(id: String, title: String, choices: List<ChordQuality>) = RelativePitchLevel(
        id = id,
        tier = RelativePitchTier.CHORD_QUALITY,
        title = title,
        prompt = "One chord. Which quality?",
        qualityChoices = choices,
    )
}
