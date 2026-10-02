package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.StockCountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockCountDao {
    @Query("SELECT * FROM stock_counts ORDER BY countDate DESC")
    fun getAllStockCounts(): Flow<List<StockCountEntity>>

    @Query("SELECT * FROM stock_counts ORDER BY countDate DESC")
    suspend fun getAllStockCountsDirect(): List<StockCountEntity>

    @Query("SELECT * FROM stock_counts WHERE restaurantId = :restaurantId ORDER BY countDate DESC")
    fun getStockCountsByRestaurant(restaurantId: String): Flow<List<StockCountEntity>>

    @Query("SELECT * FROM stock_counts WHERE id = :id LIMIT 1")
    suspend fun getStockCountById(id: String): StockCountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(stockCount: StockCountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllStockCounts(stockCounts: List<StockCountEntity>)

    @Query("DELETE FROM stock_counts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM stock_counts")
    suspend fun clearAll()
}
