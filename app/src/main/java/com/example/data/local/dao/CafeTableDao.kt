package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.CafeTableEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CafeTableDao {
    @Query("SELECT * FROM cafe_tables WHERE restaurantId = :restaurantId ORDER BY name ASC")
    fun getTablesByRestaurant(restaurantId: String): Flow<List<CafeTableEntity>>

    @Query("SELECT * FROM cafe_tables ORDER BY name ASC")
    fun getAllTables(): Flow<List<CafeTableEntity>>

    @Query("SELECT * FROM cafe_tables ORDER BY name ASC")
    suspend fun getAllTablesDirect(): List<CafeTableEntity>

    @Query("SELECT * FROM cafe_tables WHERE id = :id LIMIT 1")
    suspend fun getTableById(id: String): CafeTableEntity?

    @Query("SELECT COUNT(*) FROM cafe_tables")
    fun getCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM cafe_tables")
    suspend fun getCountDirect(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(table: CafeTableEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tables: List<CafeTableEntity>)

    @Query("UPDATE cafe_tables SET isOccupied = :isOccupied, activeBillId = :activeBillId WHERE id = :tableId")
    suspend fun updateTableOccupancy(tableId: String, isOccupied: Boolean, activeBillId: String?)

    @Query("DELETE FROM cafe_tables WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM cafe_tables")
    suspend fun clearAll()
}
