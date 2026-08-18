package com.harmonygates.sightreading

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.harmonygates.core.midi.AndroidMidiInputSource
import com.harmonygates.core.midi.MidiConnectionState
import com.harmonygates.core.midi.MidiEvent
import com.harmonygates.core.midi.MidiInputSource
import com.harmonygates.core.music.key.KeyContext
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.score.Clef
import com.harmonygates.core.music.score.DefaultPhraseGenerator
import com.harmonygates.core.music.score.PhraseSpec
import com.harmonygates.core.music.score.RationalBeat
import com.harmonygates.core.music.score.ReadingEvaluator
import com.harmonygates.core.music.score.ReadingMaterial
import com.harmonygates.core.music.score.ReadingResult
import com.harmonygates.core.music.score.ScorePhrase
import com.harmonygates.core.music.score.TimedNote
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SightReadingIntent {
    data object Start : SightReadingIntent

    data object NewPhrase : SightReadingIntent

    data object BackToSetup : SightReadingIntent

    data class ChooseMaterial(val material: ReadingMaterial) : SightReadingIntent

    data class ChooseKey(val key: String) : SightReadingIntent

    data class ChooseTempo(val bpm: Int) : SightReadingIntent

    data class ChooseMeasures(val measures: Int) : SightReadingIntent
}

enum class SightReadingPhase { SETUP, COUNT_IN, PLAYING, RESULT }

data class SightReadingUiState(
    val phase: SightReadingPhase = SightReadingPhase.SETUP,
    val material: ReadingMaterial = ReadingMaterial.SINGLE_NOTES,
    val key: String = "C",
    val tempoBpm: Int = 72,
    val measures: Int = 2,
    val phrase: ScorePhrase? = null,
    /** Counts 4, 3, 2, 1 before the phrase starts. */
    val countInBeat: Int = 0,
    val elapsedMillis: Long = 0,
    val playedNotes: List<Int> = emptyList(),
    val soundingNotes: List<Int> = emptyList(),
    val midiConnected: Boolean = false,
    val midiStatus: String = "No keyboard",
    val result: ReadingResult? = null,
) {
    val materials: List<ReadingMaterial> = ReadingMaterial.entries
    val keys: List<String> = KEYS

    val progress: Float
        get() {
            val total = phrase?.durationMillis ?: return 0f
            return if (total == 0L) 0f else (elapsedMillis.toFloat() / total).coerceIn(0f, 1f)
        }

    companion object {
        val KEYS: List<String> = listOf("C", "F", "Bb", "Eb", "G", "D", "A", "E")
    }
}

/**
 * Sight reading: read the line and play it in time.
 *
 * Phase 9 built the whole domain — exact rational durations, a phrase generator, a staff renderer
 * and an evaluator that scores pitch and rhythm separately — and then nothing ever put a clock on
 * it. This is the clock.
 *
 * The evaluator is handed timestamps rather than reading one itself, so what it decides here is
 * the same thing its tests decide. This class only supplies the two things a test does not: a
 * real phrase start, and real notes arriving from a keyboard.
 */
class SightReadingViewModel(application: Application) : AndroidViewModel(application) {

    private val midi: MidiInputSource = AndroidMidiInputSource(application, viewModelScope)
    private val generator = DefaultPhraseGenerator()
    private val evaluator = ReadingEvaluator()

    private val session = MutableStateFlow(ReadingSession())
    private var clockJob: Job? = null
    private var captureJob: Job? = null

    val state: StateFlow<SightReadingUiState> = combine(
        session,
        midi.connectionState,
        midi.activeNotes,
    ) { current, connection, active ->
        SightReadingUiState(
            phase = current.phase,
            material = current.material,
            key = current.key,
            tempoBpm = current.tempoBpm,
            measures = current.measures,
            phrase = current.phrase,
            countInBeat = current.countInBeat,
            elapsedMillis = current.elapsedMillis,
            playedNotes = current.played.map { it.note.value },
            soundingNotes = active.soundingAscending.map { it.value },
            midiConnected = connection.isReceiving,
            midiStatus = statusFor(connection),
            result = current.result,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SightReadingUiState())

    init {
        viewModelScope.launch { midi.start() }
    }

    fun onIntent(intent: SightReadingIntent) {
        when (intent) {
            SightReadingIntent.Start -> start()
            SightReadingIntent.NewPhrase -> start()
            SightReadingIntent.BackToSetup -> backToSetup()
            is SightReadingIntent.ChooseMaterial -> edit { it.copy(material = intent.material) }
            is SightReadingIntent.ChooseKey -> edit { it.copy(key = intent.key) }
            is SightReadingIntent.ChooseTempo -> edit { it.copy(tempoBpm = intent.bpm.coerceIn(40, 160)) }
            is SightReadingIntent.ChooseMeasures -> edit { it.copy(measures = intent.measures.coerceIn(1, 8)) }
        }
    }

    private fun edit(change: (ReadingSession) -> ReadingSession) {
        if (session.value.phase != SightReadingPhase.SETUP) return
        session.value = change(session.value)
    }

    private fun start() {
        clockJob?.cancel()
        captureJob?.cancel()

        val current = session.value
        val tonic = SpelledPitchClass.parseOrNull(current.key) ?: DEFAULT_TONIC
        val phrase = generator.generate(
            spec = PhraseSpec(
                key = KeyContext(tonic),
                material = current.material,
                tempoBpm = current.tempoBpm,
                measureCount = current.measures,
                clefs = listOf(Clef.TREBLE),
            ),
            seed = System.currentTimeMillis(),
        )

        session.value = current.copy(
            phase = SightReadingPhase.COUNT_IN,
            phrase = phrase,
            countInBeat = COUNT_IN_BEATS,
            elapsedMillis = 0,
            played = emptyList(),
            result = null,
        )

        clockJob = viewModelScope.launch { runPhrase(phrase) }
    }

    /**
     * Counts in, runs the phrase, then grades it.
     *
     * Notes are stamped against the moment the phrase itself began, not the count-in, because
     * that zero is what every written onset in the phrase is measured from.
     */
    private suspend fun runPhrase(phrase: ScorePhrase) {
        val beatMillis = RationalBeat.QUARTER.toMillis(phrase.tempoBpm)
        repeat(COUNT_IN_BEATS) { beat ->
            session.value = session.value.copy(countInBeat = COUNT_IN_BEATS - beat)
            delay(beatMillis)
        }

        val startedAt = System.currentTimeMillis()
        session.value = session.value.copy(phase = SightReadingPhase.PLAYING, countInBeat = 0)

        captureJob = viewModelScope.launch {
            midi.events.collect { event ->
                val note = (event as? MidiEvent.NoteOn)?.note ?: return@collect
                if (session.value.phase != SightReadingPhase.PLAYING) return@collect
                session.value = session.value.copy(
                    played = session.value.played +
                        TimedNote(note, System.currentTimeMillis() - startedAt),
                )
            }
        }

        val total = phrase.durationMillis
        while (true) {
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed >= total) break
            session.value = session.value.copy(elapsedMillis = elapsed)
            delay(TICK_MILLIS)
        }

        // A moment past the last written beat, so a note played right on it still counts.
        delay(TAIL_MILLIS)
        captureJob?.cancel()

        val played = session.value.played
        session.value = session.value.copy(
            phase = SightReadingPhase.RESULT,
            elapsedMillis = total,
            result = evaluator.evaluate(phrase, played),
        )
    }

    private fun backToSetup() {
        clockJob?.cancel()
        captureJob?.cancel()
        session.value = session.value.copy(
            phase = SightReadingPhase.SETUP,
            phrase = null,
            result = null,
            played = emptyList(),
            elapsedMillis = 0,
            countInBeat = 0,
        )
    }

    private fun statusFor(connection: MidiConnectionState): String = when (connection) {
        is MidiConnectionState.Connected -> connection.endpoint.displayName
        is MidiConnectionState.Unsupported -> "No MIDI support"
        is MidiConnectionState.NoDevice -> "No keyboard"
        is MidiConnectionState.DevicesAvailable -> "Keyboard available"
        is MidiConnectionState.Connecting -> "Connecting"
        is MidiConnectionState.Error -> "Keyboard disconnected"
    }

    override fun onCleared() {
        clockJob?.cancel()
        captureJob?.cancel()
        viewModelScope.launch { midi.stop() }
    }

    private data class ReadingSession(
        val phase: SightReadingPhase = SightReadingPhase.SETUP,
        val material: ReadingMaterial = ReadingMaterial.SINGLE_NOTES,
        val key: String = "C",
        val tempoBpm: Int = 72,
        val measures: Int = 2,
        val phrase: ScorePhrase? = null,
        val countInBeat: Int = 0,
        val elapsedMillis: Long = 0,
        val played: List<TimedNote> = emptyList(),
        val result: ReadingResult? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val COUNT_IN_BEATS = 4
        const val TICK_MILLIS = 50L
        const val TAIL_MILLIS = 600L
        val DEFAULT_TONIC: SpelledPitchClass = requireNotNull(SpelledPitchClass.parseOrNull("C"))
    }
}
