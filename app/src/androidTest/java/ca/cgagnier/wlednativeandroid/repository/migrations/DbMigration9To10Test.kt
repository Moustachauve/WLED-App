package ca.cgagnier.wlednativeandroid.repository.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ca.cgagnier.wlednativeandroid.repository.DevicesDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DbMigration9To10Test {

    private val testDb = "migration-test-9-10"
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbFile = context.getDatabasePath(testDb)

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = dbFile,
        driver = BundledSQLiteDriver(),
        databaseClass = DevicesDatabase::class,
    )

    @Before
    fun setUp() {
        context.deleteDatabase(testDb)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(testDb)
    }

    @Test
    fun migrate9To10() {
        helper.createDatabase(9).use { db ->
            db.execSQL(
                """
                INSERT INTO Device2 (macAddress, address, isHidden, originalName, customName, skipUpdateTag, branch, lastSeen)
                VALUES ('11:22:33:44:55:66', '192.168.1.100', 0, 'Original Name', 'Custom Name', 'v1.5.2', 'UNKNOWN', 0)
                """.trimIndent(),
            )

            db.execSQL(
                """
                INSERT INTO Version (tagName, name, description, isPrerelease, publishedDate, htmlUrl)
                VALUES ('v1.0.0', 'Release 1', 'Test Description', 0, '2023-01-01', 'http://example.com')
                """.trimIndent(),
            )

            db.execSQL(
                """
                INSERT INTO Asset (versionTagName, name, size, downloadUrl, assetId)
                VALUES ('v1.0.0', 'asset.bin', 1048576, 'http://example.com/asset.bin', 123)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(10, listOf(MIGRATION_9_10)).use { migratedDb ->
            migratedDb.prepare("SELECT * FROM Device2 WHERE macAddress = '11:22:33:44:55:66'").use { stmt ->
                assertTrue(stmt.step())
                val colNames = stmt.getColumnNames()
                assertEquals(1, stmt.getInt(colNames.indexOf("repositoryId")))
                assertEquals("1.5.2", stmt.getText(colNames.indexOf("skipUpdateTag")))
            }

            migratedDb.prepare("SELECT * FROM Repository WHERE id = 1").use { stmt ->
                assertTrue(stmt.step())
                val colNames = stmt.getColumnNames()
                assertEquals("Illumidel", stmt.getText(colNames.indexOf("name")))
                assertEquals("Illumidel/App-Releases", stmt.getText(colNames.indexOf("ownerAndRepo")))
                assertEquals(1, stmt.getInt(colNames.indexOf("isDefault")))
                assertEquals(1, stmt.getInt(colNames.indexOf("isEnabled")))
                assertEquals(1, stmt.getInt(colNames.indexOf("isUpdateEnabled")))
            }

            migratedDb.prepare("SELECT COUNT(*) FROM Version").use { stmt ->
                assertTrue(stmt.step())
                assertEquals(0, stmt.getInt(0))
            }

            migratedDb.prepare("SELECT COUNT(*) FROM Asset").use { stmt ->
                assertTrue(stmt.step())
                assertEquals(0, stmt.getInt(0))
            }
        }
    }
}
