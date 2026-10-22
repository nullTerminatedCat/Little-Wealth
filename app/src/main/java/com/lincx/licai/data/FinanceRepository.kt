package com.lincx.licai.data

import androidx.room.withTransaction
import com.lincx.licai.domain.TxType
import com.lincx.licai.domain.assetTemplate
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class FinanceRepository(private val db: AppDatabase) {
    private val transactions = db.transactionDao()
    private val goals = db.goalDao()
    private val assets = db.assetDao()

    fun observeTransactions(startEpochDay: Long, endEpochDay: Long): Flow<List<TransactionEntity>> {
        return transactions.observeBetween(startEpochDay, endEpochDay)
    }

    suspend fun saveTransaction(
        id: Long,
        type: TxType,
        amountCents: Long,
        categoryKey: String,
        note: String,
        occurredOn: LocalDate,
        createdAt: Long,
    ) {
        val entity = TransactionEntity(
            id = id,
            type = type,
            amountCents = amountCents,
            categoryKey = categoryKey,
            note = note.trim(),
            occurredOn = occurredOn.toEpochDay(),
            createdAt = createdAt,
        )
        if (id == 0L) transactions.insert(entity) else transactions.update(entity)
    }

    suspend fun deleteTransaction(id: Long) {
        transactions.deleteById(id)
    }

    fun observeGoals(): Flow<List<GoalWithSaved>> = goals.observeAll()

    fun observeGoal(id: Long): Flow<GoalWithSaved?> = goals.observeOne(id)

    fun observeDeposits(goalId: Long): Flow<List<GoalDepositEntity>> = goals.observeDeposits(goalId)

    fun observeDepositPace(): Flow<List<DepositPace>> = goals.observePace()

    suspend fun createGoal(
        name: String,
        targetCents: Long,
        monthlyPlanCents: Long,
        deadline: LocalDate?,
        note: String,
        initialCents: Long,
        today: LocalDate,
    ) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val id = goals.insertGoal(
                GoalEntity(
                    name = name.trim(),
                    targetCents = targetCents,
                    monthlyPlanCents = monthlyPlanCents,
                    deadlineEpochDay = deadline?.toEpochDay(),
                    note = note.trim(),
                    createdAt = now,
                ),
            )
            if (initialCents > 0) {
                goals.insertDeposit(
                    GoalDepositEntity(
                        goalId = id,
                        amountCents = initialCents,
                        occurredOn = today.toEpochDay(),
                        note = "建立目标时记入",
                        createdAt = now,
                    ),
                )
            }
        }
    }

    suspend fun updateGoal(
        id: Long,
        name: String,
        targetCents: Long,
        monthlyPlanCents: Long,
        deadline: LocalDate?,
        note: String,
        createdAt: Long,
    ) {
        goals.updateGoal(
            GoalEntity(
                id = id,
                name = name.trim(),
                targetCents = targetCents,
                monthlyPlanCents = monthlyPlanCents,
                deadlineEpochDay = deadline?.toEpochDay(),
                note = note.trim(),
                createdAt = createdAt,
            ),
        )
    }

    suspend fun deleteGoal(id: Long) {
        goals.deleteGoal(id)
    }

    suspend fun addDeposit(goalId: Long, amountCents: Long, occurredOn: LocalDate, note: String) {
        if (amountCents < 0) {
            val saved = goals.savedCents(goalId)
            if (saved + amountCents < 0) {
                throw IllegalStateException("取出金额不能超过已存金额")
            }
        }
        goals.insertDeposit(
            GoalDepositEntity(
                goalId = goalId,
                amountCents = amountCents,
                occurredOn = occurredOn.toEpochDay(),
                note = note.trim(),
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deleteDeposit(id: Long) {
        goals.deleteDeposit(id)
    }

    fun observeAssets(): Flow<List<AssetEntity>> = assets.observeAll()

    suspend fun saveAsset(
        id: Long,
        name: String,
        targetPercent: Double,
        actualCents: Long,
        note: String,
        sortOrder: Int?,
    ) {
        val order = sortOrder ?: (assets.maxSort() + 1)
        val entity = AssetEntity(
            id = id,
            name = name.trim(),
            targetPercent = targetPercent,
            actualCents = actualCents,
            sortOrder = order,
            note = note.trim(),
        )
        if (id == 0L) assets.insert(entity) else assets.update(entity)
    }

    suspend fun deleteAsset(id: Long) {
        assets.deleteById(id)
    }

    suspend fun applyTemplateIfEmpty() {
        db.withTransaction {
            if (assets.count() != 0) return@withTransaction
            assetTemplate.forEachIndexed { index, (name, percent) ->
                assets.insert(
                    AssetEntity(
                        name = name,
                        targetPercent = percent,
                        actualCents = 0,
                        sortOrder = index,
                        note = "",
                    ),
                )
            }
        }
    }
}
