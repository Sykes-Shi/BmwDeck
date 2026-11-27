package io.github.shaonialife.cardeck.launcher.settings

import io.github.shaonialife.cardeck.launcher.BaiduCruise

/** Reject stale orientation writes and keep handset-only maps untouched in every session. */
internal class OrientationSessionPolicy {
    data class Token(val generation: Long)

    private var generation = 0L
    private var active = false
    @Synchronized
    fun begin(): Token {
        generation++
        active = true
        return Token(generation)
    }

    /** 重复清理不再换代，保证 reset 排队后接着 detach 仍能完成同一轮清理。 */
    @Synchronized
    fun end(): Token {
        if (active) generation++
        active = false
        return Token(generation)
    }

    @Synchronized
    fun token(): Token = Token(generation)

    @Synchronized
    fun sessionToken(): Token? = if (active) Token(generation) else null

    /** 启动残留检查也会在 Activity 重建时运行，不能为恢复旧覆盖而终止仍活跃的连接。 */
    @Synchronized
    fun idleRecoveryToken(): Token? = if (active) null else Token(generation)

    @Synchronized
    fun isCurrent(token: Token): Boolean = token.generation == generation

    @Synchronized
    fun isActive(token: Token): Boolean = active && token.generation == generation

    fun isProtected(pkg: String): Boolean = BaiduCruise.isExcludedCarMap(pkg)

    @Synchronized
    fun permits(token: Token, pkg: String): Boolean = token.generation == generation && !isProtected(pkg)

    /** 只复原实际尝试过写入的包；偏好名单不意味着本轮修改过系统。 */
    @Synchronized
    fun resetCandidates(
        token: Token,
        attempted: Set<String>,
        legacyEnabled: Set<String> = emptySet(),
        recoverLegacy: Boolean = false,
    ): Set<String> =
        if (token.generation != generation) emptySet()
        else (attempted + if (recoverLegacy) legacyEnabled else emptySet())
            .filterTo(linkedSetOf()) { !isProtected(it) }
}
