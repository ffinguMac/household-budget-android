package com.householdbudget.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.householdbudget.app.data.local.entity.MerchantRuleEntity
import com.householdbudget.app.data.local.model.MerchantRuleRow
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: MerchantRuleEntity)

    @Query("SELECT * FROM merchant_rules WHERE merchant_key = :merchantKey AND direction = :direction")
    suspend fun getByKey(merchantKey: String, direction: String): MerchantRuleEntity?

    @Query("DELETE FROM merchant_rules WHERE merchant_key = :merchantKey AND direction = :direction")
    suspend fun deleteByKey(merchantKey: String, direction: String)

    @Query("SELECT COUNT(*) FROM merchant_rules")
    suspend fun count(): Int

    @Query(
        """
        SELECT r.merchant_key AS merchantKey, r.direction AS direction, r.display_name AS displayName,
               r.category_id AS categoryId, c.name AS categoryName, p.name AS parentCategoryName
        FROM merchant_rules r
        LEFT JOIN categories c ON c.id = r.category_id
        LEFT JOIN categories p ON p.id = c.parent_id
        ORDER BY r.display_name ASC
        """,
    )
    fun observeAll(): Flow<List<MerchantRuleRow>>
}
