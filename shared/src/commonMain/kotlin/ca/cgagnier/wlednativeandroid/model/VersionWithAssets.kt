package ca.cgagnier.wlednativeandroid.model

import androidx.room.Embedded
import androidx.room.Relation

data class VersionWithAssets(
    @Embedded
    val version: Version,
    @Relation(
        parentColumn = "id",
        entityColumn = "versionId",
    )
    val assets: List<Asset>,
) {
    /**
     * Returns `true` when [version] or the embedded version segment of any installable firmware
     * asset in [assets] matches [installedVersion] on a device associated with
     * [deviceRepositoryId].
     *
     * Several upstream and fork releases compile a version into `info.version` that matches their
     * binary filenames rather than the GitHub tag (e.g. tag `v0.15.0-rc.1` shipping
     * `WLED_0.15.0-rc1_*.bin`, or `v0.14.1-beta.32` shipping `WLEDMM_0.14.1-b32.40_*.bin`).
     */
    fun matchesInstalledVersion(installedVersion: String?, deviceRepositoryId: Long = version.repositoryId): Boolean {
        if (version.repositoryId != deviceRepositoryId || installedVersion.isNullOrBlank()) {
            return false
        }
        val normalizedInstalled = Version.normalizeTagOrVersion(installedVersion)
        if (Version.normalizeTagOrVersion(version.tagName).equals(normalizedInstalled, ignoreCase = true)) {
            return true
        }
        return assets.any { asset ->
            val parsed = parseFirmwareAssetFilename(asset.name) ?: return@any false
            Version.normalizeTagOrVersion(parsed.versionSegment)
                .equals(normalizedInstalled, ignoreCase = true)
        }
    }

    data class ParsedFirmwareAssetName(val versionSegment: String, val boardSegment: String, val extension: String)

    companion object {
        val FIRMWARE_EXTENSIONS = listOf(".bin", ".bin.gz")
        private val VERSION_SEGMENT_REGEX = Regex("""^[vV]?\d+\.\d+.*$""")
        private const val EXPECTED_ASSET_SEGMENT_COUNT = 3

        private fun extractInstallableExtension(assetName: String): String? {
            if (assetName.contains("bootloader", ignoreCase = true) ||
                assetName.contains("partition", ignoreCase = true)
            ) {
                return null
            }
            return FIRMWARE_EXTENSIONS.firstOrNull { ext ->
                assetName.endsWith(ext, ignoreCase = true)
            }
        }

        fun isInstallableAssetName(assetName: String): Boolean = extractInstallableExtension(assetName) != null

        /**
         * Parses `<prefix>_<version>_<board><.bin|.bin.gz>` from an installable firmware binary
         * filename, returning `null` for bootloaders, partition tables, non-firmware files, or
         * unrecognized names.
         */
        fun parseFirmwareAssetFilename(assetName: String): ParsedFirmwareAssetName? {
            val extension = extractInstallableExtension(assetName) ?: return null
            val parts = assetName.dropLast(extension.length).split('_', limit = EXPECTED_ASSET_SEGMENT_COUNT)
            if (parts.size != EXPECTED_ASSET_SEGMENT_COUNT) return null
            val (prefix, versionSegment, boardSegment) = parts
            if (prefix.isEmpty() || boardSegment.isEmpty() || !VERSION_SEGMENT_REGEX.matches(versionSegment)) {
                return null
            }
            return ParsedFirmwareAssetName(
                versionSegment = versionSegment,
                boardSegment = boardSegment,
                extension = extension,
            )
        }
    }
}
