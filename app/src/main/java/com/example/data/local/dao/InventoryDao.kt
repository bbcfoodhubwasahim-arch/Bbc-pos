package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.InventoryItemEntity
import com.example.data.local.entity.StockAdditionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory_items ORDER BY name ASC")
    fun getAllInventory(): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items ORDER BY name ASC")
    suspend fun getAllInventoryDirect(): List<InventoryItemEntity>

    @Query("SELECT * FROM inventory_items WHERE restaurantId = :restaurantId ORDER BY name ASC")
    fun getInventoryByRestaurant(restaurantId: String): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE currentStock <= lowStockThreshold ORDER BY currentStock ASC")
    fun getLowStockItems(): Flow<List<InventoryItemEntity>>

    @Query("SELECT COUNT(*) FROM inventory_items WHERE currentStock <= lowStockThreshold")
    fun getLowStockCount(): Flow<Int>

    @Query("SELECT * FROM inventory_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: String): InventoryItemEntity?

    @Query("SELECT COUNT(*) FROM inventory_items")
    fun getCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM inventory_items")
    suspend fun getCountDirect(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateItem(item: InventoryItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllItems(items: List<InventoryItemEntity>)

    @Query("UPDATE inventory_items SET currentStock = currentStock + :amount, updatedAt = :updatedAt WHERE id = :id")
    suspend fun addStock(id: String, amount: Double, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM inventory_items WHERE id = :id")
    suspend fun deleteItemById(id: String)

    @Query("DELETE FROM inventory_items")
    suspend fun clearAllItems()

    // Stock Additions History
    @Query("SELECT * FROM stock_additions ORDER BY timestamp DESC")
    fun getAllStockAdditions(): Flow<List<StockAdditionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockAddition(addition: StockAdditionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllStockAdditions(additions: List<StockAdditionEntity>)

    @Query("SELECT COUNT(*) FROM stock_additions")
    fun getStockAdditionsCount(): Flow<Int>
}
