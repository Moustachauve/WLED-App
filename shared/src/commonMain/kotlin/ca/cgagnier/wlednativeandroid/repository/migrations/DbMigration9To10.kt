package ca.cgagnier.wlednativeandroid.repository.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import co.touchlab.kermit.Logger

private const val TAG = "DbMigration9To10"
private val logger = Logger.withTag(TAG)
private const val FROM_VERSION = 9
private const val TO_VERSION = 10

/**
 * Migration from 9->10 adds the Repository table and updates Device, Version, and Asset tables
 * to support tracking releases using Long surrogate keys.
 *
 * It creates the Repository table and inserts the default 'Illumidel/App-Releases' repository (ID 1).
 * For Device2: adds repositoryId column with default value 1 and normalizes skipUpdateTag prefixes.
 * For Version/Asset: drops legacy cached rows and recreates tables with foreign keys so fresh
 * release metadata (including prerelease flags) is fetched cleanly from GitHub.
 */
val MIGRATION_9_10 = object : Migration(FROM_VERSION, TO_VERSION) {
    override fun migrate(connection: SQLiteConnection) {
        logger.i { "Starting migration from 9 to 10" }

        createRepositoryTable(connection)
        insertDefaultRepositories(connection)
        addRepositoryToDevice(connection)
        dropOldVersionAndAssetTables(connection)
        createNewTables(connection)
        createIndices(connection)

        logger.i { "Migration from 9 to 10 complete!" }
    }

    private fun createRepositoryTable(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `Repository` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `ownerAndRepo` TEXT NOT NULL COLLATE NOCASE,
                `description` TEXT NOT NULL,
                `htmlUrl` TEXT NOT NULL,
                `isDefault` INTEGER NOT NULL DEFAULT 0,
                `isEnabled` INTEGER NOT NULL DEFAULT 1,
                `isUpdateEnabled` INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_Repository_ownerAndRepo` ON `Repository` (`ownerAndRepo`)",
        )
    }

    private fun insertDefaultRepositories(connection: SQLiteConnection) {
        connection.execSQL(
            """
            INSERT OR IGNORE INTO Repository (id, name, ownerAndRepo, description, htmlUrl, isDefault, isEnabled, isUpdateEnabled)
            VALUES (1, 'Illumidel', 'Illumidel/App-Releases', 'Official Illumidel Firmware Repository', 'https://github.com/Illumidel/App-Releases', 1, 1, 1)
            """.trimIndent(),
        )
    }

    private fun addRepositoryToDevice(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `Device2` ADD COLUMN `repositoryId` INTEGER NOT NULL DEFAULT 1")
        connection.execSQL(
            "UPDATE `Device2` SET `skipUpdateTag` = SUBSTR(`skipUpdateTag`, 2) WHERE `skipUpdateTag` LIKE 'v%'",
        )
        logger.i { "Added repositoryId column to Device2 table" }
    }

    private fun dropOldVersionAndAssetTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `Asset`")
        connection.execSQL("DROP TABLE IF EXISTS `Version`")
    }

    private fun createNewTables(connection: SQLiteConnection) {
        // Create new Version table with repositoryId foreign key
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `Version` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `repositoryId` INTEGER NOT NULL,
                `tagName` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `isPrerelease` INTEGER NOT NULL,
                `publishedDate` TEXT NOT NULL,
                `htmlUrl` TEXT NOT NULL,
                FOREIGN KEY(`repositoryId`) REFERENCES `Repository`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )

        // Create new Asset table with versionId foreign key
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `Asset` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `versionId` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `size` INTEGER NOT NULL,
                `downloadUrl` TEXT NOT NULL,
                `assetId` INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(`versionId`) REFERENCES `Version`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
    }

    private fun createIndices(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_Version_repositoryId_tagName` ON `Version` (`repositoryId`, `tagName`)",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_Asset_versionId_name` ON `Asset` (`versionId`, `name`)",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_Asset_versionId` ON `Asset` (`versionId`)")
    }
}
