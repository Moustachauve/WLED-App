package ca.cgagnier.wlednativeandroid.service.soundsync

import co.touchlab.kermit.Logger
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "UdpSoundSyncSender"
private const val FRAME_COUNTER_MASK = 0xFF
private val logger = Logger.withTag(TAG)

/**
 * Abstraction for sending UDP Sound Sync V2 packets, allowing tests to substitute a fake sender.
 */
interface SoundSyncPacketSender {
    fun sendPacket(packet: UdpSoundSyncPacket)
    fun updateUnicastAddresses(addresses: List<String>)
    fun setSendMode(mode: SoundSyncSendMode)
    fun close()
}

/**
 * Sends UDP Sound Sync V2 packets to WLED devices using a non-blocking [DatagramChannel].
 *
 * Uses [SoundSyncSendMode.UNICAST] by default so audio data is sent directly to each
 * reachable device across routers/VLANs where multicast is filtered. In [SoundSyncSendMode.MULTICAST],
 * sends a single packet per frame to the multicast group (`239.0.0.1:11988` by default).
 *
 * Non-blocking I/O ensures a full kernel send buffer (e.g., if an ARP entry expires) drops
 * the frame instead of stalling the audio capture loop in `sock_wait_for_wmem`.
 *
 * Thread-safe: can be called from any background thread. Hostname resolution happens inside
 * [updateUnicastAddresses] on the caller's IO coroutine so [sendPacket] never blocks the
 * real-time audio capture thread on DNS lookups.
 */
class UdpSoundSyncSender(
    private val multicastAddress: String = UdpSoundSyncPacket.DEFAULT_MULTICAST_ADDRESS,
    private val port: Int = UdpSoundSyncPacket.DEFAULT_PORT,
    unicastAddresses: List<String> = emptyList(),
    sendMode: SoundSyncSendMode = SoundSyncSendMode.UNICAST,
) : SoundSyncPacketSender {
    private val closed = AtomicBoolean(false)
    private val frameCounter = AtomicInteger(0)
    private var channel: DatagramChannel? = null
    private var multicastTarget: InetSocketAddress? = null

    @Volatile
    private var currentSendMode: SoundSyncSendMode = sendMode

    @Volatile
    private var currentUnicastTargets: List<InetSocketAddress> = resolveTargets(unicastAddresses)

    /**
     * Sends a sound sync packet using the configured [SoundSyncSendMode].
     * The packet's frame counter is overridden with an auto-incrementing value.
     */
    @Synchronized
    override fun sendPacket(packet: UdpSoundSyncPacket) {
        if (closed.get()) return

        val dc = getOrCreateChannel() ?: return

        val counter = frameCounter.getAndIncrement() and FRAME_COUNTER_MASK
        val packetWithCounter = packet.copy(frameCounter = counter)
        val buffer = ByteBuffer.wrap(packetWithCounter.toByteArray())

        when (currentSendMode) {
            SoundSyncSendMode.MULTICAST -> {
                val target = getOrResolveMulticastTarget() ?: return
                runCatching {
                    dc.send(buffer, target)
                }.onFailure { logger.w(it) { "Failed to send multicast packet" } }
            }

            SoundSyncSendMode.UNICAST -> {
                for (target in currentUnicastTargets) {
                    buffer.rewind()
                    runCatching {
                        dc.send(buffer, target)
                    }.onFailure { logger.w(it) { "Failed to send unicast packet to $target" } }
                }
            }
        }
    }

    /**
     * Updates and resolves the list of unicast target addresses (e.g., when online devices change).
     * Call from an IO coroutine so hostname resolution does not block the audio capture thread.
     */
    override fun updateUnicastAddresses(addresses: List<String>) {
        if (closed.get()) return
        val resolved = resolveTargets(addresses)
        if (!closed.get()) {
            currentUnicastTargets = resolved
        }
    }

    override fun setSendMode(mode: SoundSyncSendMode) {
        currentSendMode = mode
    }

    @Synchronized
    override fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { channel?.close() }
            channel = null
            multicastTarget = null
            currentUnicastTargets = emptyList()
        }
    }

    private fun getOrCreateChannel(): DatagramChannel? {
        channel?.let { return it }
        return runCatching {
            DatagramChannel.open().apply { configureBlocking(false) }
        }.onFailure { logger.w(it) { "Failed to create UDP channel" } }
            .getOrNull()
            ?.also { channel = it }
    }

    private fun getOrResolveMulticastTarget(): InetSocketAddress? {
        multicastTarget?.let { return it }
        val resolved = runCatching {
            InetSocketAddress(InetAddress.getByName(multicastAddress), port)
        }.getOrNull()
        multicastTarget = resolved
        return resolved
    }

    private fun resolveTargets(addresses: List<String>): List<InetSocketAddress> = addresses.mapNotNull { host ->
        runCatching {
            InetSocketAddress(InetAddress.getByName(host), port)
        }.getOrNull()
    }
}
