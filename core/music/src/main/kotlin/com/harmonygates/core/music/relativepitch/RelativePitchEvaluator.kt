package com.harmonygates.core.music.relativepitch

/**
 * Where a player stands on the ladder — a pure function of accuracy evidence, the same shape as
 * `campaign.CampaignEvaluator`.
 *
 * There is deliberately no stored "unlocked" flag anywhere in this feature. A level is available
 * exactly when the evidence says the one before it is mastered, computed fresh every time, so
 * there is nothing for a flag and its evidence to drift apart on.
 */
public object RelativePitchEvaluator {

    /** [RelativePitchCurriculum.levels], each mapped to its status against [stats]. */
    public fun statuses(
        levels: List<RelativePitchLevel> = RelativePitchCurriculum.levels,
        stats: Map<String, LevelStat>,
    ): Map<String, LevelStatus> {
        var previousMastered = true
        val result = LinkedHashMap<String, LevelStatus>(levels.size)
        for (level in levels) {
            val stat = stats[level.id] ?: LevelStat.NONE
            val mastered = stat.passes(level)
            result[level.id] = when {
                mastered -> LevelStatus.MASTERED
                previousMastered -> LevelStatus.AVAILABLE
                else -> LevelStatus.LOCKED
            }
            previousMastered = mastered
        }
        return result
    }

    /** The first level that is not yet mastered — where a player picking up the ladder resumes. */
    public fun currentLevel(
        levels: List<RelativePitchLevel> = RelativePitchCurriculum.levels,
        stats: Map<String, LevelStat>,
    ): RelativePitchLevel? {
        val computed = statuses(levels, stats)
        return levels.firstOrNull { computed[it.id] != LevelStatus.MASTERED }
    }
}
