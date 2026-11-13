package ca.cgagnier.wlednativeandroid.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Entity(indices = [Index(value = ["ownerAndRepo"], unique = true)])
@OptIn(ExperimentalObjCName::class)
data class Repository(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE)
    val ownerAndRepo: String,
    @property:ObjCName("descriptionText")
    val description: String,
    val htmlUrl: String,
    @ColumnInfo(defaultValue = "0")
    val isDefault: Boolean = false,
    @ColumnInfo(defaultValue = "1")
    val isEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "1")
    val isUpdateEnabled: Boolean = true,
) {
    companion object {
        const val DEFAULT_ID = 1L
        const val DEFAULT_OWNER_REPO = "Illumidel/App-Releases"

        val BUILT_IN_REPOSITORIES = listOf(
            Repository(
                id = DEFAULT_ID,
                name = "Illumidel",
                ownerAndRepo = DEFAULT_OWNER_REPO,
                description = "Official Illumidel Firmware Repository",
                htmlUrl = "https://github.com/Illumidel/App-Releases",
                isDefault = true,
                isEnabled = true,
                isUpdateEnabled = true,
            ),
        )
    }
}
