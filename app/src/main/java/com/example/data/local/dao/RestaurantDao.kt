package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.RestaurantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RestaurantDao {
    @Query("SELECT * FROM restaurants ORDER BY name ASC")
    fun getAllRestaurants(): Flow<List<RestaurantEntity>>

    @Query("SELECT * FROM restaurants ORDER BY name ASC")
    suspend fun getAllRestaurantsDirect(): List<RestaurantEntity>

    @Query("SELECT * FROM restaurants WHERE isActive = 1 LIMIT 1")
    fun getActiveRestaurant(): Flow<RestaurantEntity?>

    @Query("SELECT * FROM restaurants WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveRestaurantDirect(): RestaurantEntity?

    @Query("SELECT * FROM restaurants WHERE id = :id LIMIT 1")
    suspend fun getRestaurantById(id: String): RestaurantEntity?

    @Query("SELECT COUNT(*) FROM restaurants")
    fun getCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM restaurants")
    suspend fun getCountDirect(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(restaurant: RestaurantEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(restaurants: List<RestaurantEntity>)

    @Query("UPDATE restaurants SET isActive = (id = :activeId)")
    suspend fun setActiveRestaurant(activeId: String)

    @Delete
    suspend fun delete(restaurant: RestaurantEntity)

    @Query("DELETE FROM restaurants WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM restaurants")
    suspend fun clearAll()
}
