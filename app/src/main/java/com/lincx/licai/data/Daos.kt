package com.lincx.licai.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query(
        """
        SELECT * FROM transactions
        WHERE occurredOn BETWEEN :start AND :end
        ORDER BY occurredOn DESC, createdAt DESC, id DESC
        """,
    )
    fun observeBetween(start: Long, end: Long): Flow<List<TransactionEntity>>

    @Insert
    suspend fun insert(entity: TransactionEntity): Long

    @Update
    suspend fun update(entity: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface GoalDao {
    @Query(
        """
        SELECT g.id, g.name, g.targetCents, g.monthlyPlanCents, g.deadlineEpochDay, g.note, g.createdAt,
               COALESCE((SELECT SUM(amountCents) FROM goal_deposits d WHERE d.goalId = g.id), 0) AS savedCents
        FROM goals g
        ORDER BY g.createdAt DESC
        """,
    )
    fun observeAll(): Flow<List<GoalWithSaved>>

    @Query(
        """
        SELECT g.id, g.name, g.targetCents, g.monthlyPlanCents, g.deadlineEpochDay, g.note, g.createdAt,
               COALESCE((SELECT SUM(amountCents) FROM goal_deposits d WHERE d.goalId = g.id), 0) AS savedCents
        FROM goals g
        WHERE g.id = :id
        """,
    )
    fun observeOne(id: Long): Flow<GoalWithSaved?>

    @Query(
        """
        SELECT * FROM goal_deposits
        WHERE goalId = :goalId
        ORDER BY occurredOn DESC, createdAt DESC, id DESC
        """,
    )
    fun observeDeposits(goalId: Long): Flow<List<GoalDepositEntity>>

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM goal_deposits WHERE goalId = :goalId")
    suspend fun savedCents(goalId: Long): Long

    @Query(
        """
        SELECT goalId, MIN(occurredOn) AS firstDay, COALESCE(SUM(amountCents), 0) AS netCents
        FROM goal_deposits
        GROUP BY goalId
        """,
    )
    fun observePace(): Flow<List<DepositPace>>

    @Insert
    suspend fun insertGoal(entity: GoalEntity): Long

    @Update
    suspend fun updateGoal(entity: GoalEntity)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteGoal(id: Long)

    @Insert
    suspend fun insertDeposit(entity: GoalDepositEntity): Long

    @Query("DELETE FROM goal_deposits WHERE id = :id")
    suspend fun deleteDeposit(id: Long)
}

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<AssetEntity>>

    @Query("SELECT COUNT(*) FROM assets")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM assets")
    suspend fun maxSort(): Int

    @Insert
    suspend fun insert(entity: AssetEntity): Long

    @Update
    suspend fun update(entity: AssetEntity)

    @Query("DELETE FROM assets WHERE id = :id")
    suspend fun deleteById(id: Long)
}
