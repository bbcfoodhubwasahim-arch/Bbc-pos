package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.StockTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockTransactionDao {
    @Query("SELECT * FROM stock_transactions WHERE inventoryItemId = :itemId ORDER BY timestamp DESC")
    fun getTransactionsForItem(itemId: String): Flow<List<StockTransactionEntity>>

    @Query("SELECT * FROM stock_transactions WHERE inventoryItemId = :itemId ORDER BY timestamp DESC")
    suspend fun getTransactionsForItemDirect(itemId: String): List<StockTransactionEntity>

    @Query("SELECT * FROM stock_transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<StockTransactionEntity>>

    @Query("SELECT * FROM stock_transactions ORDER BY timestamp DESC")
    suspend fun getAllTransactionsDirect(): List<StockTransactionEntity>

    @Query("SELECT * FROM stock_transactions WHERE billId = :billId ORDER BY timestamp DESC")
    suspend fun getTransactionsForBill(billId: String): List<StockTransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: StockTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<StockTransactionEntity>)

    @Query("DELETE FROM stock_transactions")
    suspend fun clearAll()
}
