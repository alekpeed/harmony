package com.harmonygates.core.music.performance

/** Where a capture is in its cycle (05_MIDI_INPUT_ENGINE.md §6). */
public enum class CaptureState {
    /** Not capturing. */
    IDLE,

    /** Armed, waiting for the first note. */
    ARMED,

    /** Notes are arriving. */
    COLLECTING,

    /** Collection is finished and the attempt has been handed over. */
    COMPLETED,
}
