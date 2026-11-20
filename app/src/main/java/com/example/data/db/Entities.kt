package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.CommissionMode
import com.example.domain.CommissionMovement
import com.example.domain.CommissionMovementCategory
import com.example.domain.Operation
import com.example.domain.OperationType
import com.example.domain.Person
import com.example.domain.SyncEntityType
import com.example.domain.SyncTombstone

@Entity(tableName = "persons")
data class PersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val receiptName: String?,
    val commissionRate: Double,
    val defaultCommissionMode: String,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "operations",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["personId"])]
)
data class OperationEntity(
    @PrimaryKey val id: String,
    val personId: String,
    val type: String,
    val date: String,
    val amount: Double,
    val commissionRateSnapshot: Double?,
    val commissionMode: String?,
    val commissionAmount: Double?,
    val note: String?,
    val billBreakdown: Map<Int, Int>?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "commission_movements")
data class CommissionMovementEntity(
    @PrimaryKey val id: String,
    val date: String,
    val amount: Double,
    val category: String,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "sync_tombstones",
    primaryKeys = ["workspaceId", "entityType", "entityId"]
)
data class SyncTombstoneEntity(
    val workspaceId: String,
    val entityType: String,
    val entityId: String,
    val deletedAt: Long
)

fun PersonEntity.toDomain(): Person = Person(
    id = id,
    name = name,
    receiptName = receiptName,
    commissionRate = commissionRate,
    defaultCommissionMode = CommissionMode.fromWire(defaultCommissionMode),
    archived = archived,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Person.toEntity(): PersonEntity = PersonEntity(
    id = id,
    name = name,
    receiptName = receiptName?.takeIf { it.isNotBlank() },
    commissionRate = commissionRate,
    defaultCommissionMode = defaultCommissionMode.wireValue,
    archived = archived,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun OperationEntity.toDomain(): Operation = Operation(
    id = id,
    personId = personId,
    type = OperationType.fromWire(type),
    date = date,
    amount = amount,
    commissionRateSnapshot = commissionRateSnapshot,
    commissionMode = commissionMode?.let { CommissionMode.fromWire(it) },
    commissionAmount = commissionAmount,
    note = note,
    billBreakdown = billBreakdown,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Operation.toEntity(): OperationEntity = OperationEntity(
    id = id,
    personId = personId,
    type = type.wireValue,
    date = date,
    amount = amount,
    commissionRateSnapshot = commissionRateSnapshot,
    commissionMode = commissionMode?.wireValue,
    commissionAmount = commissionAmount,
    note = note?.takeIf { it.isNotBlank() },
    billBreakdown = billBreakdown?.filterValues { it > 0 }?.takeIf { it.isNotEmpty() },
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun CommissionMovementEntity.toDomain(): CommissionMovement = CommissionMovement(
    id = id,
    date = date,
    amount = amount,
    category = CommissionMovementCategory.fromWire(category),
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun CommissionMovement.toEntity(): CommissionMovementEntity = CommissionMovementEntity(
    id = id,
    date = date,
    amount = amount,
    category = category.wireValue,
    note = note?.takeIf { it.isNotBlank() },
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun SyncTombstoneEntity.toDomain(): SyncTombstone? {
    val type = SyncEntityType.fromWire(entityType) ?: return null
    return SyncTombstone(
        workspaceId = workspaceId,
        entityType = type,
        entityId = entityId,
        deletedAt = deletedAt
    )
}

fun SyncTombstone.toEntity(): SyncTombstoneEntity = SyncTombstoneEntity(
    workspaceId = workspaceId,
    entityType = entityType.wireValue,
    entityId = entityId,
    deletedAt = deletedAt
)
