package com.harmonygates.core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** How accidentals are chosen when the music does not decide for itself. */
public enum class AccidentalPreference {
    /** Follow the key signature. The musically correct default. */
    FROM_KEY,

    PREFER_SHARPS,

    PREFER_FLATS,
}

/** Which assistance channels are on by default. Phase 7 turns this into full profiles. */
public data class AssistanceDefaults(
    val showSpelledNoteNames: Boolean = false,
    val showKeyboardTargets: Boolean = false,
    val showInversionLabel: Boolean = true,
)

/**
 * Everything the player has chosen.
 *
 * 11_DATA_MODEL_AND_PERSISTENCE.md §1 lists exactly what belongs in preferences and what does
 * not. Nothing here affects whether an answer is correct: a preference changes what the player
 * is shown and which device is listened to, never what counts as a C major seventh.
 */
public data class HarmonySettings(
    /** The MIDI device to reconnect to, by name. Null means "whatever is plugged in". */
    val preferredMidiDevice: String? = null,
    /** The player's keyboard span, so exercises are not generated off the end of it. */
    val keyboardLowNote: Int = DEFAULT_LOW_NOTE,
    val keyboardHighNote: Int = DEFAULT_HIGH_NOTE,
    val assistance: AssistanceDefaults = AssistanceDefaults(),
    val metronomeVolume: Float = DEFAULT_VOLUME,
    val instrumentVolume: Float = DEFAULT_VOLUME,
    val accidentalPreference: AccidentalPreference = AccidentalPreference.FROM_KEY,
    val showStaffNotation: Boolean = false,
    /** Left-handed players and low-slung tablets want the keyboard elsewhere. */
    val keyboardOnLeft: Boolean = false,
    val hasSeenIntroduction: Boolean = false,
) {
    public val keyboardRange: IntRange get() = keyboardLowNote..keyboardHighNote

    public companion object {
        /** An 88-key piano, which is the widest thing the player is likely to own. */
        public const val DEFAULT_LOW_NOTE: Int = 21
        public const val DEFAULT_HIGH_NOTE: Int = 108
        public const val DEFAULT_VOLUME: Float = 0.8f
    }
}

private val Context.harmonyDataStore: DataStore<Preferences> by preferencesDataStore(name = "harmony-settings")

/**
 * Preferences, on DataStore.
 *
 * Reads are a flow rather than a snapshot so a setting changed on the settings screen reaches
 * a session already in progress: 10_ANDROID_ARCHITECTURE.md §3's unidirectional flow, applied
 * to something as small as which notes the player's keyboard has.
 */
public class HarmonyPreferences(context: Context) {

    private val store = context.applicationContext.harmonyDataStore

    public val settings: Flow<HarmonySettings> = store.data.map { it.toSettings() }

    public suspend fun setPreferredMidiDevice(name: String?) {
        store.edit { prefs ->
            if (name == null) prefs.remove(Keys.MIDI_DEVICE) else prefs[Keys.MIDI_DEVICE] = name
        }
    }

    public suspend fun setKeyboardRange(range: IntRange) {
        require(!range.isEmpty()) { "A keyboard with no keys is not a keyboard" }
        store.edit { prefs ->
            prefs[Keys.KEYBOARD_LOW] = range.first
            prefs[Keys.KEYBOARD_HIGH] = range.last
        }
    }

    public suspend fun setAssistanceDefaults(defaults: AssistanceDefaults) {
        store.edit { prefs ->
            prefs[Keys.ASSIST_NOTE_NAMES] = defaults.showSpelledNoteNames
            prefs[Keys.ASSIST_KEYBOARD] = defaults.showKeyboardTargets
            prefs[Keys.ASSIST_INVERSION] = defaults.showInversionLabel
        }
    }

    public suspend fun setVolumes(instrument: Float, metronome: Float) {
        store.edit { prefs ->
            prefs[Keys.VOLUME_INSTRUMENT] = instrument.coerceIn(0f, 1f)
            prefs[Keys.VOLUME_METRONOME] = metronome.coerceIn(0f, 1f)
        }
    }

    public suspend fun setAccidentalPreference(preference: AccidentalPreference) {
        store.edit { it[Keys.ACCIDENTALS] = preference.name }
    }

    public suspend fun setShowStaffNotation(show: Boolean) {
        store.edit { it[Keys.STAFF_NOTATION] = show }
    }

    public suspend fun setKeyboardOnLeft(onLeft: Boolean) {
        store.edit { it[Keys.KEYBOARD_LEFT] = onLeft }
    }

    public suspend fun setSeenIntroduction(seen: Boolean) {
        store.edit { it[Keys.SEEN_INTRO] = seen }
    }

    private fun Preferences.toSettings(): HarmonySettings = HarmonySettings(
        preferredMidiDevice = this[Keys.MIDI_DEVICE],
        keyboardLowNote = this[Keys.KEYBOARD_LOW] ?: HarmonySettings.DEFAULT_LOW_NOTE,
        keyboardHighNote = this[Keys.KEYBOARD_HIGH] ?: HarmonySettings.DEFAULT_HIGH_NOTE,
        assistance = AssistanceDefaults(
            showSpelledNoteNames = this[Keys.ASSIST_NOTE_NAMES] == true,
            showKeyboardTargets = this[Keys.ASSIST_KEYBOARD] == true,
            showInversionLabel = this[Keys.ASSIST_INVERSION] != false,
        ),
        metronomeVolume = this[Keys.VOLUME_METRONOME] ?: HarmonySettings.DEFAULT_VOLUME,
        instrumentVolume = this[Keys.VOLUME_INSTRUMENT] ?: HarmonySettings.DEFAULT_VOLUME,
        accidentalPreference = this[Keys.ACCIDENTALS]
            ?.let { name -> AccidentalPreference.entries.firstOrNull { it.name == name } }
            ?: AccidentalPreference.FROM_KEY,
        showStaffNotation = this[Keys.STAFF_NOTATION] == true,
        keyboardOnLeft = this[Keys.KEYBOARD_LEFT] == true,
        hasSeenIntroduction = this[Keys.SEEN_INTRO] == true,
    )

    private object Keys {
        val MIDI_DEVICE = stringPreferencesKey("midi_device")
        val KEYBOARD_LOW = intPreferencesKey("keyboard_low")
        val KEYBOARD_HIGH = intPreferencesKey("keyboard_high")
        val ASSIST_NOTE_NAMES = booleanPreferencesKey("assist_note_names")
        val ASSIST_KEYBOARD = booleanPreferencesKey("assist_keyboard")
        val ASSIST_INVERSION = booleanPreferencesKey("assist_inversion")
        val VOLUME_INSTRUMENT = floatPreferencesKey("volume_instrument")
        val VOLUME_METRONOME = floatPreferencesKey("volume_metronome")
        val ACCIDENTALS = stringPreferencesKey("accidentals")
        val STAFF_NOTATION = booleanPreferencesKey("staff_notation")
        val KEYBOARD_LEFT = booleanPreferencesKey("keyboard_left")
        val SEEN_INTRO = booleanPreferencesKey("seen_intro")
    }
}
