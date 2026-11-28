package ca.cgagnier.wlednativeandroid.service.soundsync

import kotlinx.serialization.Serializable

/**
 * UDP delivery mode used by Sound Sync on platforms that support both unicast and multicast.
 *
 * - [UNICAST]: Sends individual UDP packets to each online visible WLED device, which works
 *   across routers, mesh nodes, and VLANs that block multicast traffic.
 * - [MULTICAST]: Sends a single UDP packet per frame to `239.0.0.1`, scaling cleanly to many
 *   devices when the local network supports IP multicast.
 */
@Serializable
enum class SoundSyncSendMode {
    UNICAST,
    MULTICAST,
}
