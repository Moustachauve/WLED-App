package ca.cgagnier.wlednativeandroid.service.soundsync.dsp

import kotlinx.serialization.Serializable

/**
 * Automatic Gain Control (AGC) operating modes modeled after WLED's Sound Settings.
 *
 * Each mode defines the tracking parameters that set its character:
 * - [targetLevel]: Target peak amplitude (`0.0..1.0`) that AGC aims to maintain.
 * - [peakDecayTimeSec]: Exponential decay time constant (in seconds) for the tracked input peak.
 * - [slowFollowTimeSec]: Gain adjustment time constant (in seconds) when the amplified signal
 *   sits inside the normal operating zone (`squelchAmplitude + ZONE_LOW..ZONE_HIGH`),
 *   preserving musical dynamics.
 * - [fastFollowTimeSec]: Gain adjustment time constant (in seconds) when the amplified signal
 *   enters the emergency zone (below `squelchAmplitude + ZONE_LOW` or above `ZONE_HIGH`),
 *   reacting quickly to prevent sustained clipping or dead LEDs.
 */
@Serializable
enum class AgcMode(
    val targetLevel: Float,
    val peakDecayTimeSec: Float,
    val slowFollowTimeSec: Float,
    val fastFollowTimeSec: Float,
) {
    /** Smoothly follows changes in volume. A good default for most setups. */
    NORMAL(
        targetLevel = 0.60f,
        peakDecayTimeSec = 4.0f,
        slowFollowTimeSec = 3.0f,
        fastFollowTimeSec = 0.20f,
    ),

    /** Reacts quickly to volume changes for a more dramatic LED response to dynamics. */
    VIVID(
        targetLevel = 0.65f,
        peakDecayTimeSec = 1.5f,
        slowFollowTimeSec = 1.2f,
        fastFollowTimeSec = 0.12f,
    ),

    /** Slower to adjust; works well for GEQ effects or music with wide dynamic range. */
    LAZY(
        targetLevel = 0.55f,
        peakDecayTimeSec = 8.0f,
        slowFollowTimeSec = 6.0f,
        fastFollowTimeSec = 0.35f,
    ),
}
