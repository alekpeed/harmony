package com.harmonygates.voiceleadingmenu

import androidx.compose.runtime.Immutable

@Immutable
data class VoiceLeadingMenuState(
    val exercise: VoiceLeadingExercise = VoiceLeadingExercise.GuideTones,
    val key: String = "C",
    val motion: VoiceLeadingMotion = VoiceLeadingMotion.Nearest,
    val range: VoiceLeadingRange = VoiceLeadingRange.Middle,
    val difficulty: VoiceLeadingDifficulty = VoiceLeadingDifficulty.Medium,
    val tempoBpm: Int = 72,
    val repetitions: Int = 8,
    val metronome: Boolean = true,
    val showHints: Boolean = true,
    val collapsed: Boolean = false,
)

enum class VoiceLeadingExercise(val label: String) {
    GuideTones("Guide tones"),
    Shells("Shell voicings"),
    Triads("Triad movement"),
    SeventhChords("7th chords"),
    DropTwo("Drop 2"),
    CommonTone("Common-tone"),
}

enum class VoiceLeadingMotion(val label: String) {
    Nearest("Nearest"),
    Contrary("Contrary"),
    Oblique("Oblique"),
    Mixed("Mixed"),
}

enum class VoiceLeadingRange(val label: String) {
    Low("Low"),
    Middle("Middle"),
    High("High"),
    Full("Full"),
}

enum class VoiceLeadingDifficulty(val label: String) {
    Easy("Easy"),
    Medium("Medium"),
    Hard("Hard"),
}

val VoiceLeadingKeys = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
