package ca.cgagnier.wlednativeandroid.repository

import androidx.room.Room
import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

internal const val DATABASE_FILE_NAME = "devices_database.db"
private const val DATABASE_SUBDIRECTORY = "Database"
private val SQLITE_SIDECAR_SUFFIXES = listOf("", "-wal", "-shm")
private val logger = Logger.withTag("DevicesDatabase.ios")

@OptIn(ExperimentalForeignApi::class)
internal fun documentDirectory(): String {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documentDirectory?.path)
}

@OptIn(ExperimentalForeignApi::class)
internal fun applicationSupportDatabaseDirectory(fileManager: NSFileManager = NSFileManager.defaultManager): String? {
    val appSupportUrl = fileManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    ) ?: return null
    val dbDirUrl = appSupportUrl.URLByAppendingPathComponent(DATABASE_SUBDIRECTORY, isDirectory = true) ?: return null
    val path = dbDirUrl.path ?: return null
    if (!fileManager.fileExistsAtPath(path)) {
        val created = fileManager.createDirectoryAtPath(
            path = path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        if (!created) return null
    }
    return path
}

/**
 * Copies `devices_database.db` (and any `-wal`/`-shm` sidecars) from [fromDirectory] to [toDirectory]
 * if needed, removing the legacy files only after all existing files have been copied.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun migrateDatabaseDirectoryIfNeeded(
    fromDirectory: String,
    toDirectory: String,
    fileManager: NSFileManager = NSFileManager.defaultManager,
    dbFileName: String = DATABASE_FILE_NAME,
): Boolean {
    if (fromDirectory == toDirectory) return true
    if (fileManager.fileExistsAtPath("$toDirectory/$dbFileName")) return true
    if (!fileManager.fileExistsAtPath("$fromDirectory/$dbFileName")) return true

    val existingSuffixes = SQLITE_SIDECAR_SUFFIXES.filter {
        fileManager.fileExistsAtPath("$fromDirectory/$dbFileName$it")
    }
    for (suffix in existingSuffixes) {
        val src = "$fromDirectory/$dbFileName$suffix"
        val dst = "$toDirectory/$dbFileName$suffix"
        fileManager.removeItemAtPath(dst, error = null)
        if (!fileManager.copyItemAtPath(src, toPath = dst, error = null)) {
            logger.w { "Failed to migrate database file $src to $dst; falling back to $fromDirectory" }
            existingSuffixes.forEach { fileManager.removeItemAtPath("$toDirectory/$dbFileName$it", error = null) }
            return false
        }
    }
    existingSuffixes.forEach { fileManager.removeItemAtPath("$fromDirectory/$dbFileName$it", error = null) }
    return true
}

@OptIn(ExperimentalForeignApi::class)
internal fun resolveDatabaseDirectory(fileManager: NSFileManager = NSFileManager.defaultManager): String {
    val docDir = documentDirectory()
    val appSupportDbDir = applicationSupportDatabaseDirectory(fileManager) ?: return docDir
    return if (migrateDatabaseDirectoryIfNeeded(docDir, appSupportDbDir, fileManager)) appSupportDbDir else docDir
}

fun DevicesDatabase.Companion.createDatabase(): DevicesDatabase {
    val dbFilePath = resolveDatabaseDirectory() + "/" + DATABASE_FILE_NAME
    return Room.databaseBuilder<DevicesDatabase>(
        name = dbFilePath,
    )
        .configureDevicesDatabase()
        .build()
}

fun DevicesDatabase.Companion.createInMemoryDatabase(): DevicesDatabase =
    Room.inMemoryDatabaseBuilder<DevicesDatabase>()
        .configureDevicesDatabase()
        .build()

/** Creates a new [DeviceRepository] backed by this database. Callers should keep and reuse the instance. */
fun DevicesDatabase.createDeviceRepository(): DeviceRepository = DeviceRepository(deviceDao())

/** Creates a new [VersionWithAssetsRepository] backed by this database. Callers should keep and reuse the instance. */
fun DevicesDatabase.createVersionWithAssetsRepository(): VersionWithAssetsRepository =
    VersionWithAssetsRepository(this, repositoryDao(), versionDao(), assetDao())
