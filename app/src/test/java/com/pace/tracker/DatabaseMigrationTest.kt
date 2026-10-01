package com.pace.tracker

import com.pace.tracker.data.db.PaceDatabase
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Room validates the database against its schema after a migration and crashes on any mismatch.
 * This compares the hand-written v1→v2 migration with the schema Room exported for v2 at build time.
 */
class DatabaseMigrationTest {
    private val schema by lazy {
        JSONObject(File("schemas/com.pace.tracker.data.db.PaceDatabase/2.json").readText()).getJSONObject("database")
    }

    private fun entity(table: String): JSONObject {
        val entities = schema.getJSONArray("entities")
        return (0 until entities.length()).map { entities.getJSONObject(it) }.first { it.getString("tableName") == table }
    }

    @Test
    fun foodItemTableMatchesSchema() {
        val expected = entity("food_item").getString("createSql").replace("\${TABLE_NAME}", "food_item")
        assertEquals(expected, PaceDatabase.SQL_CREATE_FOOD_ITEM)
        assertEquals(0, entity("food_item").optJSONArray("indices")?.length() ?: 0)
    }

    @Test
    fun mealProteinColumnMatchesSchema() {
        val fields = entity("meal").getJSONArray("fields")
        val protein = (0 until fields.length()).map { fields.getJSONObject(it) }.first { it.getString("columnName") == "protein" }
        assertEquals("REAL", protein.getString("affinity"))
        assertFalse(protein.optBoolean("notNull", false))
        assertFalse(protein.has("defaultValue"))
        assertTrue(PaceDatabase.SQL_ADD_MEAL_PROTEIN.contains("`protein` REAL"))
        assertEquals(2, schema.getInt("version"))
    }
}
