package ca.cgagnier.wlednativeandroid.ui

import android.content.Context
import androidx.core.content.edit
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import io.github.z4kn4fein.semver.toVersionOrNull
import java.util.concurrent.TimeUnit

/**
 * Popup "Update 1.6 available" when one or more controllers can get a newer
 * IllumiDEL V1 firmware. It uses the update detection that already exists
 * ([DeviceWithState.updateVersionTag], which respects the channel and "Skip").
 *
 * Rules (same as iOS):
 *  - shown once per launch at most, only for online, visible controllers,
 *  - after "Later", the same version is shown again only after 7 days,
 *  - a new version is shown right away,
 *  - versions 2.x and up are ignored (move to the new cloud app, which has its own warning card).
 */
object UpdatePrompt {
    data class Prompt(val tag: String, val devices: List<DeviceWithState>)

    private const val PREFS = "illumidel_update_prompt"
    private const val KEY_LAST_TAG = "last_tag"
    private const val KEY_LAST_SHOWN = "last_shown"
    private const val DAYS_BEFORE_SHOWING_AGAIN = 7L
    private const val MAX_SUPPORTED_MAJOR_VERSION = 2

    var shownThisSession = false
        private set

    fun check(context: Context, devices: List<DeviceWithState>): Prompt? {
        if (shownThisSession) return null
        val candidates = devices.filter(::isEligibleDevice)
        val tag = candidates
            .mapNotNull { it.updateVersionTag }
            .maxWithOrNull(compareBy { stripVersionPrefix(it).toVersionOrNull(strict = false) })
            ?: return null

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastTag = prefs.getString(KEY_LAST_TAG, null)
        val lastShown = prefs.getLong(KEY_LAST_SHOWN, 0L)
        val waitMillis = TimeUnit.DAYS.toMillis(DAYS_BEFORE_SHOWING_AGAIN)
        if (tag == lastTag && System.currentTimeMillis() - lastShown < waitMillis) return null
        return Prompt(tag, candidates)
    }

    private fun isEligibleDevice(device: DeviceWithState): Boolean {
        if (!device.isOnline || device.device.isHidden) return false
        val tag = device.updateVersionTag ?: return false
        val major = majorVersion(tag) ?: return false
        return major < MAX_SUPPORTED_MAJOR_VERSION
    }

    fun markShown(context: Context, tag: String) {
        shownThisSession = true
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_LAST_TAG, tag)
            putLong(KEY_LAST_SHOWN, System.currentTimeMillis())
        }
    }

    private fun stripVersionPrefix(tag: String): String = tag.trim().removePrefix("v").removePrefix("V")

    // "v1.6.0" -> 1
    private fun majorVersion(tag: String): Int? = stripVersionPrefix(tag)
        .substringBefore('.')
        .toIntOrNull()
}
