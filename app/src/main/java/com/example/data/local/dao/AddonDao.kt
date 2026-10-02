package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.AddonDefinitionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AddonDao {
    @Query("SELECT * FROM addons ORDER BY groupName ASC, name ASC")
    fun getAllAddons(): Flow<List<AddonDefinitionEntity>>

    @Query("SELECT * FROM addons ORDER BY groupName ASC, name ASC")
    suspend fun getAllAddonsDirect(): List<AddonDefinitionEntity>

    @Query("SELECT * FROM addons WHERE isActive = 1 ORDER BY groupName ASC, name ASC")
    fun getActiveAddons(): Flow<List<AddonDefinitionEntity>>

    @Query("SELECT * FROM addons WHERE isActive = 1 ORDER BY groupName ASC, name ASC")
    suspend fun getActiveAddonsDirect(): List<AddonDefinitionEntity>

    @Query("SELECT * FROM addons WHERE id = :id LIMIT 1")
    suspend fun getAddonById(id: String): AddonDefinitionEntity?

    @Query("SELECT COUNT(*) FROM addons")
    fun getCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(addon: AddonDefinitionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(addons: List<AddonDefinitionEntity>)

    @Query("DELETE FROM addons WHERE id = :id")
    suspend fun deleteById(id: String)
}
