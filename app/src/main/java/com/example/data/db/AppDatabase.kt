package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PersonEntity::class,
        OperationEntity::class,
        CommissionMovementEntity::class,
        SyncTombstoneEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun operationDao(): OperationDao
    abstract fun commissionMovementDao(): CommissionMovementDao
    abstract fun syncTombstoneDao(): SyncTombstoneDao
    abstract fun backupTransactionDao(): BackupTransactionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS persons_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        receiptName TEXT,
                        commissionRate REAL NOT NULL,
                        defaultCommissionMode TEXT NOT NULL,
                        archived INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO persons_new (id, name, receiptName, commissionRate, defaultCommissionMode, archived, createdAt, updatedAt)
                    SELECT id, name, receiptName, commissionRate, defaultCommissionMode, archived,
                        COALESCE(CAST(strftime('%s', substr(createdAt, 1, 19)) AS INTEGER) * 1000, CAST(strftime('%s', 'now') AS INTEGER) * 1000),
                        COALESCE(CAST(strftime('%s', substr(createdAt, 1, 19)) AS INTEGER) * 1000, CAST(strftime('%s', 'now') AS INTEGER) * 1000)
                    FROM persons
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE persons")
                db.execSQL("ALTER TABLE persons_new RENAME TO persons")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS operations_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        personId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        date TEXT NOT NULL,
                        amount REAL NOT NULL,
                        commissionRateSnapshot REAL,
                        commissionMode TEXT,
                        commissionAmount REAL,
                        note TEXT,
                        billBreakdown TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(personId) REFERENCES persons(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO operations_new (id, personId, type, date, amount, commissionRateSnapshot, commissionMode, commissionAmount, note, billBreakdown, createdAt, updatedAt)
                    SELECT id, personId, type, date, amount, commissionRateSnapshot, commissionMode, commissionAmount, note, billBreakdown,
                        COALESCE(CAST(strftime('%s', substr(createdAt, 1, 19)) AS INTEGER) * 1000, CAST(strftime('%s', 'now') AS INTEGER) * 1000),
                        COALESCE(CAST(strftime('%s', substr(createdAt, 1, 19)) AS INTEGER) * 1000, CAST(strftime('%s', 'now') AS INTEGER) * 1000)
                    FROM operations
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE operations")
                db.execSQL("ALTER TABLE operations_new RENAME TO operations")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_operations_personId ON operations(personId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS commission_movements_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        date TEXT NOT NULL,
                        amount REAL NOT NULL,
                        category TEXT NOT NULL,
                        note TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO commission_movements_new (id, date, amount, category, note, createdAt, updatedAt)
                    SELECT id, date, amount, category, note,
                        COALESCE(CAST(strftime('%s', substr(createdAt, 1, 19)) AS INTEGER) * 1000, CAST(strftime('%s', 'now') AS INTEGER) * 1000),
                        COALESCE(CAST(strftime('%s', substr(createdAt, 1, 19)) AS INTEGER) * 1000, CAST(strftime('%s', 'now') AS INTEGER) * 1000)
                    FROM commission_movements
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE commission_movements")
                db.execSQL("ALTER TABLE commission_movements_new RENAME TO commission_movements")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_tombstones (
                        workspaceId TEXT NOT NULL,
                        entityType TEXT NOT NULL,
                        entityId TEXT NOT NULL,
                        deletedAt INTEGER NOT NULL,
                        PRIMARY KEY(workspaceId, entityType, entityId)
                    )
                    """.trimIndent()
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "control_extracciones.db"
            )
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
