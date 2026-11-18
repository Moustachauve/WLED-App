package ca.cgagnier.wlednativeandroid.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.z4kn4fein.semver.toVersionOrNull
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import io.github.z4kn4fein.semver.Version as SemVersion

private const val ISO_DATE_PREFIX_LENGTH = 10

@Entity(
    indices = [Index(value = ["repositoryId", "tagName"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = Repository::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("repositoryId"),
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
@OptIn(ExperimentalObjCName::class)
data class Version(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val repositoryId: Long,
    val tagName: String,
    val name: String,
    @property:ObjCName("descriptionText")
    val description: String,
    val isPrerelease: Boolean,
    val publishedDate: String,
    val htmlUrl: String,
) {
    /**
     * Whether this release belongs to the Beta/pre-release channel.
     *
     * Checks GitHub's `isPrerelease` flag, SemVer pre-release identifiers (e.g. `-beta`, `-b2`,
     * `-rc1`), and dot-separated fork pre-release suffixes (e.g. `v0.14.0.b27` in WLED-MM)
     * because some forks publish beta tags without marking them as pre-releases on GitHub.
     */
    val isBetaRelease: Boolean
        get() = isPrerelease || isPreReleaseTag(tagName)

    /**
     * Returns the trimmed release [name] when it is non-blank and meaningfully distinct from
     * [tagName] (ignoring a leading `v`/`V` prefix and letter case), or `null` otherwise.
     */
    val distinctReleaseName: String?
        get() {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return null
            val isSameAsTag = normalizeTagOrVersion(trimmed).equals(
                normalizeTagOrVersion(tagName),
                ignoreCase = true,
            )
            return if (isSameAsTag) null else trimmed
        }

    /**
     * Formatted release subtitle combining [distinctReleaseName] and the ISO date prefix of
     * [publishedDate], separated by `" • "`.
     */
    val displaySubtitle: String
        get() {
            val datePrefix = publishedDate.take(ISO_DATE_PREFIX_LENGTH)
            return listOfNotNull(
                distinctReleaseName,
                datePrefix.takeIf { it.isNotBlank() },
            ).joinToString(" • ")
        }

    companion object {
        fun normalizeTagOrVersion(value: String): String = value.removePrefix("v").removePrefix("V")

        private val DOT_PRERELEASE_REGEX =
            Regex("""^(\d+(?:\.\d+){1,2})\.([A-Za-z][0-9A-Za-z.-]*)$""")

        /**
         * Parses [tagName] as a relaxed SemVer, normalizing dot-separated pre-release suffixes
         * such as `v0.14.0.b27` into `v0.14.0-b27` when standard parsing fails.
         */
        fun parseSemVer(tagName: String): SemVersion? {
            val normalized = normalizeTagOrVersion(tagName)
            return normalized.toVersionOrNull(strict = false)
                ?: DOT_PRERELEASE_REGEX.matchEntire(normalized)
                    ?.let { "${it.groupValues[1]}-${it.groupValues[2]}".toVersionOrNull(strict = false) }
        }

        /**
         * Returns `true` when [tagOrVersion] carries a SemVer or fork pre-release suffix
         * (such as `0.15.0-b2`, `14.5.0-beta`, or `v0.14.0.b27`).
         */
        fun isPreReleaseTag(tagOrVersion: String): Boolean = parseSemVer(tagOrVersion)?.isPreRelease == true

        fun getPreviewVersion(): Version = Version(
            repositoryId = 1,
            tagName = "v1.0.0",
            name = "new version",
            description = "this is a test version",
            isPrerelease = false,
            publishedDate = "2024-10-13T15:54:31Z",
            htmlUrl = "https://github.com/",
        )
    }
}
