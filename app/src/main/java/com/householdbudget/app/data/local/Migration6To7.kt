package com.householdbudget.app.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.domain.category.CategoryPath
import com.householdbudget.app.domain.category.FixedCategories
import com.householdbudget.app.domain.category.LegacyCategoryMapping
import com.householdbudget.app.domain.category.LegacyTarget

/**
 * v6 → v7: 사용자 카테고리 트리를 [FixedCategories.TREE]로 바꾼다.
 * 거래는 [LegacyCategoryMapping]으로 새 소분류에 옮기고(기타로 가면 메모에 옛 이름을 남김),
 * 예산은 잎이 가장 많이 가는 새 대분류로 합치고, 반복 규칙 테이블은 지운다.
 * Room이 한 트랜잭션에서 돌리므로 중간에 실패하면 통째로 롤백된다.
 */
val MIGRATION_6_7 =
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. 옛 카테고리를 먼저 다 읽는다(커서를 연 채로 INSERT하지 않는다).
            val old = mutableListOf<OldCategory>()
            db.query("SELECT id, name, kind, parent_id FROM categories").use { c ->
                while (c.moveToNext()) {
                    old += OldCategory(c.getLong(0), c.getString(1), CategoryKind.fromStorage(c.getString(2)), if (c.isNull(3)) null else c.getLong(3))
                }
            }
            val oldById = old.associateBy { it.id }

            // 2. 고정 목록 INSERT. AUTOINCREMENT라 새 id는 모든 옛 id보다 커서 3번의 UPDATE가 겹치지 않는다.
            val leafIds = mutableMapOf<CategoryPath, Long>()
            val parentIds = mutableMapOf<Pair<CategoryKind, String>, Long>()
            FixedCategories.TREE.forEachIndexed { i, p ->
                val pid = db.insertCategory(p.name, p.kind, null, i, p.icon)
                parentIds[p.kind to p.name] = pid
                p.children.forEachIndexed { j, child ->
                    leafIds[CategoryPath(p.kind, p.name, child)] = db.insertCategory(child, p.kind, pid, j, null)
                }
            }

            // 옛 대분류에 직접 달린 거래·예산은 "기본" 소분류로 본다.
            fun pathOf(c: OldCategory): Pair<String, String> =
                if (c.parentId == null) c.name to "기본" else (oldById[c.parentId]?.name ?: "") to c.name
            fun targetOf(c: OldCategory): LegacyTarget = pathOf(c).let { (p, ch) -> LegacyCategoryMapping.map(c.kind, p, ch) }

            // 3. 거래 옮기기. transactions.kind는 그대로 둔다.
            for (c in old) {
                val target = targetOf(c)
                val newId = leafIds.getValue(target.path)
                if (target.tagMemo) {
                    val tag = pathOf(c).let { (p, ch) -> "[$p>$ch]" }
                    db.execSQL(
                        "UPDATE transactions SET category_id = ?, " +
                            "memo = CASE WHEN memo = '' THEN ? ELSE ? || ' ' || memo END WHERE category_id = ?",
                        arrayOf<Any>(newId, tag, tag, c.id),
                    )
                } else {
                    db.execSQL("UPDATE transactions SET category_id = ? WHERE category_id = ?", arrayOf<Any>(newId, c.id))
                }
            }

            // 4. 예산: 대상 새 대분류별로 금액 합산, enabled는 하나라도 켜져 있으면 켬.
            val treeOrder = FixedCategories.TREE.mapIndexed { i, p -> (p.kind to p.name) to i }.toMap()
            fun budgetTarget(c: OldCategory): Pair<CategoryKind, String> {
                if (c.parentId != null) return targetOf(c).path.let { it.kind to it.parent }
                val counts = old.filter { it.parentId == c.id }.groupingBy { targetOf(it).path.let { p -> p.kind to p.parent } }.eachCount()
                if (counts.isEmpty()) return targetOf(c).path.let { it.kind to it.parent }
                return counts.entries.sortedWith(compareBy({ -it.value }, { treeOrder.getValue(it.key) })).first().key
            }
            val merged = linkedMapOf<Long, Pair<Long, Boolean>>()
            db.query("SELECT category_id, monthly_amount_minor, enabled FROM category_budgets").use { b ->
                while (b.moveToNext()) {
                    // 없는 카테고리를 가리키는 예산은 쓸모가 없으니 버린다.
                    val c = oldById[b.getLong(0)] ?: continue
                    val pid = parentIds.getValue(budgetTarget(c))
                    val (sum, enabled) = merged[pid] ?: (0L to false)
                    merged[pid] = (sum + b.getLong(1)) to (enabled || b.getInt(2) != 0)
                }
            }
            db.execSQL("DELETE FROM category_budgets")
            for ((pid, v) in merged) {
                db.execSQL(
                    "INSERT INTO category_budgets (category_id, monthly_amount_minor, enabled) VALUES (?, ?, ?)",
                    arrayOf<Any>(pid, v.first, if (v.second) 1 else 0),
                )
            }

            // 5. 반복 규칙 제거(옛 카테고리를 RESTRICT로 잡고 있으므로 6번보다 먼저).
            db.execSQL("DROP TABLE IF EXISTS recurring_rules")

            // 6. 옛 카테고리 삭제: 잎 먼저, 그다음 대분류.
            for (c in old.sortedBy { it.parentId == null }) {
                db.execSQL("DELETE FROM categories WHERE id = ?", arrayOf<Any>(c.id))
            }
        }
    }

private data class OldCategory(val id: Long, val name: String, val kind: CategoryKind, val parentId: Long?)

private fun SupportSQLiteDatabase.insertCategory(name: String, kind: CategoryKind, parentId: Long?, sortOrder: Int, icon: String?): Long =
    insert(
        "categories",
        SQLiteDatabase.CONFLICT_ABORT,
        ContentValues().apply {
            put("name", name)
            put("kind", kind.storage)
            put("parent_id", parentId)
            put("sort_order", sortOrder)
            put("icon", icon)
        },
    )
