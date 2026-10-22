package com.lincx.licai.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lincx.licai.domain.TxType

@Entity(
    tableName = "transactions",
    indices = [Index("occurredOn"), Index("type")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TxType,
    val amountCents: Long,
    val categoryKey: String,
    val note: String,
    val occurredOn: Long,
    val createdAt: Long,
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetCents: Long,
    val monthlyPlanCents: Long,
    val deadlineEpochDay: Long?,
    val note: String,
    val createdAt: Long,
)

@Entity(
    tableName = "goal_deposits",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("goalId")],
)
data class GoalDepositEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val amountCents: Long,
    val occurredOn: Long,
    val note: String,
    val createdAt: Long,
)

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetPercent: Double,
    val actualCents: Long,
    val sortOrder: Int,
    val note: String,
)

data class GoalWithSaved(
    @Embedded val goal: GoalEntity,
    val savedCents: Long,
)

data class DepositPace(
    val goalId: Long,
    val firstDay: Long,
    val netCents: Long,
)
