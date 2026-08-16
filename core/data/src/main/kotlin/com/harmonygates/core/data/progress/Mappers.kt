package com.harmonygates.core.data.progress

import com.harmonygates.core.data.db.AttemptEntity
import com.harmonygates.core.data.db.SkillMasteryEntity
import com.harmonygates.core.music.exercise.SkillId
import com.harmonygates.core.music.mastery.ErrorClass
import com.harmonygates.core.music.mastery.Evidence
import com.harmonygates.core.music.mastery.MasteryEvent
import com.harmonygates.core.music.mastery.SkillMastery
import com.harmonygates.core.music.performance.PerformanceError
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.session.AttemptRecord
import java.time.Instant

/**
 * Translation between the domain and its stored form.
 *
 * All of it is here rather than spread across the DAOs so that the encodings — how a set of
 * roots becomes a column, how a histogram round-trips — can be read in one place and tested as
 * a unit. Every one of them is text: 11_DATA_MODEL_AND_PERSISTENCE.md §4 requires old rows to
 * stay readable after the domain classes change shape, and a serialised object graph would not.
 */
internal object Mappers {

    private const val SEPARATOR = ","
    private const val PAIR_SEPARATOR = ":"

    // --- Mastery ---------------------------------------------------------------------------

    fun toEntity(profileId: String, mastery: SkillMastery): SkillMasteryEntity = SkillMasteryEntity(
        profileId = profileId,
        skillId = mastery.skillId.value,
        estimate = mastery.estimate,
        attempts = mastery.attempts,
        successfulAttempts = mastery.successfulAttempts,
        recentWeightedAccuracy = mastery.recentWeightedAccuracy,
        medianResponseMillis = mastery.medianResponseMillis,
        lastPracticedAtEpochMillis = mastery.lastPracticedAt?.toEpochMilli(),
        nextReviewAtEpochMillis = mastery.nextReviewAt?.toEpochMilli(),
        errorHistogram = encodeHistogram(mastery.errorHistogram),
        rootsCovered = mastery.rootsCovered.joinToString(SEPARATOR) { it.toString() },
        recentEvidence = mastery.recentEvidence.joinToString(SEPARATOR) { it.name },
    )

    fun toDomain(entity: SkillMasteryEntity): SkillMastery = SkillMastery(
        skillId = SkillId(entity.skillId),
        estimate = entity.estimate,
        attempts = entity.attempts,
        successfulAttempts = entity.successfulAttempts,
        recentWeightedAccuracy = entity.recentWeightedAccuracy,
        medianResponseMillis = entity.medianResponseMillis,
        lastPracticedAt = entity.lastPracticedAtEpochMillis?.let(Instant::ofEpochMilli),
        nextReviewAt = entity.nextReviewAtEpochMillis?.let(Instant::ofEpochMilli),
        errorHistogram = decodeHistogram(entity.errorHistogram),
        rootsCovered = split(entity.rootsCovered).mapNotNull { SpelledPitchClass.parseOrNull(it) }.toSet(),
        recentEvidence = split(entity.recentEvidence).mapNotNull { name ->
            Evidence.entries.firstOrNull { it.name == name }
        },
    )

    private fun encodeHistogram(histogram: Map<ErrorClass, Int>): String =
        histogram.entries.sortedBy { it.key.name }
            .joinToString(SEPARATOR) { "${it.key.name}$PAIR_SEPARATOR${it.value}" }

    private fun decodeHistogram(encoded: String): Map<ErrorClass, Int> =
        split(encoded).mapNotNull { entry ->
            val (name, count) = entry.split(PAIR_SEPARATOR).let {
                it.firstOrNull() to it.getOrNull(1)?.toIntOrNull()
            }
            val errorClass = ErrorClass.entries.firstOrNull { it.name == name }
            if (errorClass != null && count != null) errorClass to count else null
        }.toMap()

    private fun split(encoded: String): List<String> =
        encoded.split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }

    // --- Attempts --------------------------------------------------------------------------

    @Suppress("LongParameterList")
    fun toEntity(
        id: String,
        sessionId: String,
        record: AttemptRecord,
        evidence: Evidence,
        at: Instant,
    ): AttemptEntity {
        val errors = record.result.semanticErrors
        return AttemptEntity(
            id = id,
            sessionId = sessionId,
            exerciseDefinitionId = record.instance.definitionId.value,
            exerciseSeed = record.instance.seed,
            skillIds = record.instance.skillIds.joinToString(SEPARATOR) { it.value },
            chordSymbol = record.instance.chord.symbol,
            rootPitchClass = record.instance.chord.root.toString(),
            startedAtEpochMillis = at.toEpochMilli(),
            firstInputAtEpochMillis = record.result.timing?.responseLatencyMillis
                ?.let { at.toEpochMilli() + it },
            completedAtEpochMillis = at.toEpochMilli(),
            verdict = record.result.verdict.name,
            hintsUsed = record.hintsUsed,
            skipped = record.skipped,
            responseMillis = record.result.timing?.responseLatencyMillis,
            onsetSpreadMillis = record.result.timing?.onsetSpreadMillis,
            evidence = evidence.name,
            errorClasses = errors.joinToString(SEPARATOR) { ErrorClass.of(it).name },
            expectedSnapshot = expectedSnapshot(record),
            performedSnapshot = performedSnapshot(record),
            semanticErrors = errors.joinToString(SEPARATOR) { describe(it) },
        )
    }

    fun toStored(entity: AttemptEntity): StoredAttempt = StoredAttempt(
        id = entity.id,
        sessionId = entity.sessionId,
        exerciseDefinitionId = entity.exerciseDefinitionId,
        exerciseSeed = entity.exerciseSeed,
        skillIds = split(entity.skillIds).map { SkillId(it) },
        chordSymbol = entity.chordSymbol,
        verdict = entity.verdict,
        hintsUsed = entity.hintsUsed,
        skipped = entity.skipped,
        completedAt = Instant.ofEpochMilli(entity.completedAtEpochMillis),
        responseMillis = entity.responseMillis,
        onsetSpreadMillis = entity.onsetSpreadMillis,
        expected = entity.expectedSnapshot,
        performed = entity.performedSnapshot,
        errors = split(entity.semanticErrors),
    )

    /** Reads a stored attempt back as evidence, for a mastery rebuild. */
    fun toMasteryEvents(entity: AttemptEntity): List<MasteryEvent> {
        if (entity.skipped) return emptyList()
        val evidence = Evidence.entries.firstOrNull { it.name == entity.evidence } ?: return emptyList()
        val errors = split(entity.errorClasses).mapNotNull { name ->
            ErrorClass.entries.firstOrNull { it.name == name }
        }
        val at = Instant.ofEpochMilli(entity.completedAtEpochMillis)
        return split(entity.skillIds).map { skillId ->
            MasteryEvent(
                skillId = SkillId(skillId),
                evidence = evidence,
                at = at,
                responseMillis = entity.responseMillis,
                errors = errors,
                root = SpelledPitchClass.parseOrNull(entity.rootPitchClass),
            )
        }
    }

    /**
     * What the exercise asked for, in a form that outlives the content that produced it.
     *
     * Plain text rather than a serialised requirement: §3 wants the snapshot legible after the
     * definitions change, and a JSON blob of a class that no longer exists is not legible.
     */
    private fun expectedSnapshot(record: AttemptRecord): String = buildString {
        append(record.instance.chord.symbol)
        append(" [")
        append(record.instance.spelledTones.joinToString(" "))
        append("]")
        if (record.instance.targetNotes.isNotEmpty()) {
            append(" targets=")
            append(record.instance.targetNotes.joinToString("/"))
        }
        append(" inversion=")
        append(record.instance.inversion.name)
    }

    private fun performedSnapshot(record: AttemptRecord): String =
        record.attempt.finalEffectiveNotes.joinToString(" ") { it.value.toString() }
            .ifEmpty { "(nothing played)" }

    /** A stable, parseable description of one finding. Never a player-facing sentence. */
    private fun describe(error: PerformanceError): String = when (error) {
        is PerformanceError.WrongRoot -> "WRONG_ROOT expected=${error.expected} played=${error.played}"
        is PerformanceError.WrongQuality ->
            "WRONG_QUALITY expected=${error.expectedDegree.symbol} played=${error.playedDegree?.symbol}"

        is PerformanceError.MissingDegree -> "MISSING ${error.tone.degree?.symbol ?: error.tone.pitchClass}"
        is PerformanceError.WrongAlteration ->
            "WRONG_ALTERATION expected=${error.expected.symbol} played=${error.played.symbol}"

        is PerformanceError.WrongBass -> "WRONG_BASS expected=${error.expected} played=${error.played?.value}"
        is PerformanceError.WrongTopNote ->
            "WRONG_TOP expected=${error.expected?.value} played=${error.played?.value}"

        is PerformanceError.ExtraTone -> "EXTRA note=${error.note.value} degree=${error.degree?.symbol}"
        is PerformanceError.RegisterViolation -> "REGISTER note=${error.note.value} allowed=${error.allowedRange}"
        is PerformanceError.OnsetSpreadViolation ->
            "ONSET spread=${error.spreadMillis}ms allowed=${error.allowedMillis}ms"

        is PerformanceError.RhythmViolation -> "RHYTHM beat=${error.expectedBeat} error=${error.errorMillis}ms"
        PerformanceError.NoNotesPlayed -> "NOTHING_PLAYED"
    }
}
