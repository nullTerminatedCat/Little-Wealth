package com.lincx.licai.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.lincx.licai.domain.TxType

class Converters {
    @TypeConverter
    fun fromType(value: TxType): String = value.name

    @TypeConverter
    fun toType(value: String): TxType = TxType.valueOf(value)
}

@Database(
    entities = [
        TransactionEntity::class,
        GoalEntity::class,
        GoalDepositEntity::class,
        AssetEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun goalDao(): GoalDao
    abstract fun assetDao(): AssetDao
}
