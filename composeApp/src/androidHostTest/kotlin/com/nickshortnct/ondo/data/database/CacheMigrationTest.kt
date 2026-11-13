package com.nickshortnct.ondo.data.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CacheMigrationTest {
	@Test fun supportedUpgradesPreserveUserRowsAndBackfillSearch() = runTest {
		for (version in listOf(20, 21)) {
			BundledSQLiteDriver().open(":memory:").use { db ->
				createFixture(db, "CacheDatabase", version)
				db.execSQL("UPDATE ArtistEntity SET name = 'Beyoncé'")
				db.execSQL("UPDATE SongEntity SET title = 'Déjà Vu', artistName = 'Beyoncé'")
				val action = row(db, "SELECT * FROM SyncActionEntity")
				val membership = row(db, "SELECT * FROM PlaylistSongCrossRef")
				if (version == 20) CacheDatabase_AutoMigration_20_21_Impl().migrate(db)
				CacheDatabase_AutoMigration_21_22_Impl().migrate(db)
				assertEquals(action, row(db, "SELECT * FROM SyncActionEntity").take(action.size))
				assertEquals(membership, row(db, "SELECT * FROM PlaylistSongCrossRef"))
				for (table in LibrarySearchTable.entries) {
					assertTrue(row(db, "SELECT searchText FROM ${table.entity}").single().isNotBlank())
				}
				val query = librarySearchQuery("BEYONCE deja", LibrarySearchTable.SONG)
				db.prepare(query.sql).use { statement ->
					query.getBindingFunction().invoke(statement)
					assertTrue(statement.step())
					assertEquals("seed", statement.getText(0))
				}
			}
		}
		// Audio-file records live in their own database and are untouched by cache upgrades.
		BundledSQLiteDriver().open(":memory:").use { downloads ->
			createFixture(downloads, "DownloadDatabase", 4)
			assertEquals(listOf("seed"), row(downloads, "SELECT songId FROM DownloadEntity"))
		}
	}

	private fun createFixture(db: SQLiteConnection, name: String, version: Int) {
		val schema = Json.parseToJsonElement(File("schemas/com.nickshortnct.ondo.data.database.$name/$version.json").readText())
			.jsonObject.getValue("database").jsonObject.getValue("entities").jsonArray
		db.execSQL("PRAGMA foreign_keys = OFF")
		for (entity in schema) {
			val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
			db.execSQL(entity.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
			val fields = entity.jsonObject.getValue("fields").jsonArray
			val columns = fields.map { it.jsonObject.getValue("columnName").jsonPrimitive.content }
			val values = fields.map { field ->
				val item = field.jsonObject
				if (item.getValue("affinity").jsonPrimitive.content == "TEXT") "'seed'" else "0"
			}
			db.execSQL("INSERT INTO `$table` (${columns.joinToString { "`$it`" }}) VALUES (${values.joinToString()})")
		}
		db.execSQL("PRAGMA foreign_keys = ON")
	}

	private fun row(db: SQLiteConnection, sql: String): List<String> = db.prepare(sql).use { statement ->
		assertTrue(statement.step(), sql)
		(0 until statement.getColumnCount()).map { if (statement.isNull(it)) "NULL" else statement.getText(it) }
	}
}
