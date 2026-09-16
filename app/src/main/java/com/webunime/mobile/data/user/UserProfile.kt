package com.webunime.mobile.data.user

data class UserProfile(
    val uid: String? = null,
    val displayName: String? = null,
    val email: String? = null,
    val photoUrl: String? = null,
    val keys: Int = 0,
    val gems: Int = 0,
    val xp: Int = 0,
    val level: Int = 1,
    val isPremium: Boolean = false,
    val premiumUntilMs: Long = 0L,
    val animeSubs: List<String> = emptyList(),
) {
    fun effectivePremium(now: Long = System.currentTimeMillis()): Boolean =
        isPremium && (premiumUntilMs <= 0L || premiumUntilMs > now)

    fun xpToNextLevel(): Int = level * 50

    fun canWatchFree(now: Long = System.currentTimeMillis()): Boolean =
        effectivePremium(now) || keys > 0
}

object EconomyRules {
    fun applyXp(profile: UserProfile, gainedXp: Int, gemsPerLevel: Int): UserProfile {
        var xp = profile.xp + gainedXp
        var level = profile.level
        var gems = profile.gems
        var need = level * 50
        while (xp >= need) {
            xp -= need
            level += 1
            gems += gemsPerLevel
            need = level * 50
        }
        return profile.copy(xp = xp, level = level, gems = gems)
    }
}
