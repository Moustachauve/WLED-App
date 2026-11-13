package ca.cgagnier.wlednativeandroid.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import ca.cgagnier.wlednativeandroid.model.DEFAULT_WLED_AP_IP
import ca.cgagnier.wlednativeandroid.ui.MainActivity

/**
 * Represents a parsed deep link result.
 */
sealed class DeepLink {
    /** Deep link with a MAC address identifier. */
    data class MacAddress(val mac: String) : DeepLink()

    /** Deep link with a network address (IP or hostname). */
    data class Address(val address: String) : DeepLink()

    /** Deep link to AP mode (4.3.2.1). */
    data object ApMode : DeepLink()
}

/**
 * Handles parsing of deep links from intents.
 *
 * Supports:
 * - illumidel://{mac_address} - e.g., illumidel://AABBCCDDEEFF
 * - illumidel://device/{mac_address} - e.g., illumidel://device/AABBCCDDEEFF
 * - illumidel://{ip_or_hostname} - e.g., illumidel://192.168.1.50
 * - http://4.3.2.1 - Default AP mode IP
 */
class DeepLinkHandler {

    companion object {
        private const val SCHEME_ILLUMIDEL = "illumidel"
        private const val SCHEME_HTTP = "http"
        private const val HOST_DEVICE = "device"
        private val MAC_ADDRESS_REGEX = Regex("^[0-9A-Fa-f]{12}$")
        private val SEPARATED_MAC_REGEX =
            Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$|^([0-9A-Fa-f]{2}-){5}[0-9A-Fa-f]{2}$")

        /**
         * Creates an Intent to open the app with the specified device.
         *
         * @param context The context to create the intent with
         * @param macAddress The device's MAC address
         * @return An Intent configured to open the device in the app
         */
        fun createDeviceIntent(context: Context, macAddress: String): Intent =
            Intent(Intent.ACTION_VIEW, "$SCHEME_ILLUMIDEL://$macAddress".toUri())
                .setClass(context, MainActivity::class.java)
                .setPackage(context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    /**
     * Parses an Intent and returns the corresponding [DeepLink] if valid.
     *
     * @param intent The intent to parse
     * @return The parsed [DeepLink] or null if not a valid deep link
     */
    fun parseIntent(intent: Intent?): DeepLink? {
        if (intent?.action != Intent.ACTION_VIEW) {
            return null
        }
        return parseUri(intent.data)
    }

    /**
     * Parses a URI and returns the corresponding [DeepLink] if valid.
     *
     * @param uri The URI to parse
     * @return The parsed [DeepLink] or null if not a valid deep link URI
     */
    fun parseUri(uri: Uri?): DeepLink? {
        uri ?: return null

        return when (uri.scheme?.lowercase()) {
            SCHEME_ILLUMIDEL -> parseIllumidelScheme(uri)
            SCHEME_HTTP -> parseHttpScheme(uri)
            else -> null
        }
    }

    private fun parseIllumidelScheme(uri: Uri): DeepLink? {
        val segments = uri.pathSegments.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
        val authority = uri.authority?.trim()?.takeIf { it.isNotEmpty() }
        val host = uri.host?.trim()?.takeIf { it.isNotEmpty() }

        if (host.equals(HOST_DEVICE, ignoreCase = true)) {
            val rawMac = segments.singleOrNull() ?: return null
            return normalizeMacAddress(rawMac)?.let(DeepLink::MacAddress)
        }
        if (segments.isNotEmpty()) {
            return null
        }
        if (authority != null) {
            normalizeMacAddress(authority)?.let { return DeepLink.MacAddress(it) }
        }
        return host?.let { normalizeMacAddress(it)?.let(DeepLink::MacAddress) ?: DeepLink.Address(it) }
    }

    private fun normalizeMacAddress(identifier: String): String? = when {
        isMacAddress(identifier) -> identifier.uppercase()

        SEPARATED_MAC_REGEX.matches(identifier) ->
            identifier.replace(":", "").replace("-", "").uppercase()

        else -> null
    }

    private fun parseHttpScheme(uri: Uri): DeepLink? {
        val host = uri.host ?: return null

        return if (host == DEFAULT_WLED_AP_IP) {
            DeepLink.ApMode
        } else {
            null
        }
    }

    /**
     * Checks if the given string is a valid MAC address format (12 hex characters).
     *
     * @param identifier The string to check
     * @return true if it's a valid MAC address format
     */
    fun isMacAddress(identifier: String): Boolean = MAC_ADDRESS_REGEX.matches(identifier)
}
