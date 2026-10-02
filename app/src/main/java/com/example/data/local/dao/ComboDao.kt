package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.ComboEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ComboDao {
    @Query("SELECT * FROM combos WHERE restaurantId = :restaurantId ORDER BY createdAt DESC")
    fun getCombosByRestaurant(restaurantId: String): Flow<List<ComboEntity>>

    @Query("SELECT * FROM combos WHERE restaurantId = :restaurantId AND isActive = 1 ORDER BY createdAt DESC")
    fun getActiveCombosByRestaurant(restaurantId: String): Flow<List<ComboEntity>>

    @Query("SELECT * FROM combos WHERE id = :id LIMIT 1")
    suspend fun getComboById(id: String): ComboEntity?

    @Query("SELECT * FROM combos ORDER BY createdAt DESC")
    suspend fun getAllCombosDirect(): List<ComboEntity>

    @Query("SELECT COUNT(*) FROM combos")
    fun getCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(combo: ComboEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(combos: List<ComboEntity>)

    @Delete
    suspend fun delete(combo: ComboEntity)

    @Query("DELETE FROM combos WHERE id = :comboId")
    suspend fun deleteById(comboId: String)
}
