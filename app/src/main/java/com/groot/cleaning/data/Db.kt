package com.groot.cleaning.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "selected_apps")
data class SelectedApp(@PrimaryKey val packageName: String, val appName: String, val operation: String)

@Entity(tableName = "selected_accounts")
data class SelectedAccount(@PrimaryKey val label: String)

@Entity(tableName = "logs")
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val target: String,
    val operation: String,
    val status: String,
    val durationMs: Long,
    val message: String,
    val steps: String
)

@Dao
interface GcDao {
    @Query("SELECT * FROM selected_apps ORDER BY appName") fun apps(): Flow<List<SelectedApp>>
    @Query("SELECT * FROM selected_apps ORDER BY appName") suspend fun appsNow(): List<SelectedApp>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertApp(a: SelectedApp)
    @Query("DELETE FROM selected_apps WHERE packageName = :p") suspend fun deleteApp(p: String)
    @Query("DELETE FROM selected_apps") suspend fun clearApps()

    @Query("SELECT * FROM selected_accounts ORDER BY label") fun accounts(): Flow<List<SelectedAccount>>
    @Query("SELECT * FROM selected_accounts ORDER BY label") suspend fun accountsNow(): List<SelectedAccount>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAccount(a: SelectedAccount)
    @Query("DELETE FROM selected_accounts WHERE label = :l") suspend fun deleteAccount(l: String)

    @Query("SELECT * FROM logs ORDER BY time DESC LIMIT 300") fun logs(): Flow<List<LogEntry>>
    @Insert suspend fun insertLog(l: LogEntry)
    @Query("DELETE FROM logs") suspend fun clearLogs()
}

@Database(entities = [SelectedApp::class, SelectedAccount::class, LogEntry::class], version = 1, exportSchema = false)
abstract class GcDb : RoomDatabase() {
    abstract fun dao(): GcDao

    companion object {
        @Volatile private var inst: GcDb? = null
        fun get(c: Context): GcDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, GcDb::class.java, "groot.db").build().also { inst = it }
        }
    }
}
