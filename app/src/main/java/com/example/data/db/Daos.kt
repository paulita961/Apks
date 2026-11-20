package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM persons ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons ORDER BY createdAt ASC")
    suspend fun getAllOnce(): List<PersonEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(person: PersonEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(persons: List<PersonEntity>)

    @Query("DELETE FROM persons WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM persons")
    suspend fun deleteAll()
}

@Dao
interface OperationDao {
    @Query("SELECT * FROM operations")
    fun observeAll(): Flow<List<OperationEntity>>

    @Query("SELECT * FROM operations")
    suspend fun getAllOnce(): List<OperationEntity>

    @Query("SELECT id FROM operations WHERE personId = :personId")
    suspend fun getIdsForPerson(personId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(operation: OperationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(operations: List<OperationEntity>)

    @Query("DELETE FROM operations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM operations")
    suspend fun deleteAll()
}

@Dao
interface CommissionMovementDao {
    @Query("SELECT * FROM commission_movements")
    fun observeAll(): Flow<List<CommissionMovementEntity>>

    @Query("SELECT * FROM commission_movements")
    suspend fun getAllOnce(): List<CommissionMovementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(movement: CommissionMovementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movements: List<CommissionMovementEntity>)

    @Query("DELETE FROM commission_movements WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM commission_movements")
    suspend fun deleteAll()
}

@Dao
interface SyncTombstoneDao {
    @Query("SELECT * FROM sync_tombstones WHERE workspaceId = :workspaceId")
    suspend fun getByWorkspace(workspaceId: String): List<SyncTombstoneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tombstones: List<SyncTombstoneEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tombstone: SyncTombstoneEntity)

    @Query("DELETE FROM sync_tombstones WHERE workspaceId = :workspaceId")
    suspend fun deleteByWorkspace(workspaceId: String)

    @Query("DELETE FROM sync_tombstones WHERE deletedAt < :cutoffMs")
    suspend fun purgeOlderThan(cutoffMs: Long)
}

@Dao
interface BackupTransactionDao {
    @Transaction
    suspend fun replaceAllData(
        personDao: PersonDao,
        operationDao: OperationDao,
        movementDao: CommissionMovementDao,
        persons: List<PersonEntity>,
        operations: List<OperationEntity>,
        movements: List<CommissionMovementEntity>
    ) {
        operationDao.deleteAll()
        movementDao.deleteAll()
        personDao.deleteAll()
        personDao.insertAll(persons)
        operationDao.insertAll(operations)
        movementDao.insertAll(movements)
    }

    @Transaction
    suspend fun applyMergedSyncData(
        personDao: PersonDao,
        operationDao: OperationDao,
        movementDao: CommissionMovementDao,
        tombstoneDao: SyncTombstoneDao,
        workspaceId: String,
        persons: List<PersonEntity>,
        operations: List<OperationEntity>,
        movements: List<CommissionMovementEntity>,
        tombstones: List<SyncTombstoneEntity>
    ) {
        operationDao.deleteAll()
        movementDao.deleteAll()
        personDao.deleteAll()
        tombstoneDao.deleteByWorkspace(workspaceId)
        personDao.insertAll(persons)
        operationDao.insertAll(operations)
        movementDao.insertAll(movements)
        if (tombstones.isNotEmpty()) {
            tombstoneDao.upsertAll(tombstones)
        }
    }
}
