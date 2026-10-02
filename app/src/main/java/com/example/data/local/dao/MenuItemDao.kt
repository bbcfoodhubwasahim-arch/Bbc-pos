package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.MenuItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MenuItemDao {
    @Query("SELECT * FROM menu_items ORDER BY categoryName ASC, name ASC")
    fun getAllMenuItems(): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items ORDER BY categoryName ASC, name ASC")
    suspend fun getAllMenuItemsDirect(): List<MenuItemEntity>

    @Query("SELECT * FROM menu_items WHERE categoryId = :categoryId ORDER BY name ASC")
    fun getMenuItemsByCategory(categoryId: String): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items WHERE id = :id LIMIT 1")
    suspend fun getMenuItemById(id: String): MenuItemEntity?

    @Query("SELECT COUNT(*) FROM menu_items")
    fun getCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM menu_items")
    suspend fun getCountDirect(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(item: MenuItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MenuItemEntity>)

    @Query("DELETE FROM menu_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM menu_items")
    suspend fun clearAll()
}
