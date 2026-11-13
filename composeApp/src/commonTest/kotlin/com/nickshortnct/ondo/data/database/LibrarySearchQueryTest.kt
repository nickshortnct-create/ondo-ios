package com.nickshortnct.ondo.data.database

import androidx.sqlite.SQLiteStatement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibrarySearchQueryTest {
	@Test
	fun allWordsAreBoundAndRequiredAcrossNullableMetadata() {
		val query = librarySearchQuery("  Daft   Punk\tGet Lucky  ", LibrarySearchTable.SONG)
		val values = query.captureBindings()
		assertEquals(listOf("%daft%", "%punk%", "%get%", "%lucky%"), values.take(4))
		assertEquals(listOf("Daft Punk Get Lucky", "Daft Punk Get Lucky%"), values.takeLast(2))
		val where = query.sql.substringBefore("ORDER BY")
		assertEquals(3, Regex(" AND ").findAll(where).count())
		assertTrue("searchText" in where)
		assertEquals(values.size, query.sql.count { it == '?' })
		assertFalse("Daft" in query.sql)
	}

	@Test
	fun sqlLookingTextAndWildcardCharactersStayLiteralBoundValues() {
		val query = librarySearchQuery("' OR 1=1 -- % _ \\", LibrarySearchTable.SONG)
		val values = query.captureBindings()
		assertEquals(listOf("%or%", "%1%", "%\\%%", "%\\_%", "%\\\\%"), values.take(5))
		assertTrue(values.last().endsWith("%"))
		assertEquals(values.size, query.sql.count { it == '?' })
		assertFalse("1=1" in query.sql)
		assertFalse("--" in query.sql)
		assertTrue("ESCAPE '\\'" in query.sql)
	}

	@Test
	fun resultLimitsAndExactBeforePrefixRankingCoverEveryTable() {
		for (table in LibrarySearchTable.entries) {
			val query = librarySearchQuery("Love", table)
			val limit = if (table == LibrarySearchTable.SONG) 200 else 100
			assertTrue(query.sql.endsWith("LIMIT $limit"), table.name)
			assertTrue("THEN 0" in query.sql && "THEN 1 ELSE 2 END" in query.sql, table.name)
			assertEquals(listOf("%love%", "Love", "Love%"), query.captureBindings(), table.name)
		}
		assertTrue("searchText" in librarySearchQuery("Daft", LibrarySearchTable.ALBUM).sql)
	}

	@Test
	fun blankQueriesMatchNothingAndLongQueriesKeepEveryWordWithBoundedParameters() {
		for (table in LibrarySearchTable.entries) {
			for (text in listOf("", "  ", "\t\n")) {
				val query = librarySearchQuery(text, table)
				assertTrue(query.sql.endsWith("WHERE 0"), table.name)
				assertEquals(emptyList(), query.captureBindings(), table.name)
			}
		}
		val words = (1..100).map { "word$it" }
		val query = librarySearchQuery(words.joinToString(" "), LibrarySearchTable.SONG)
		val values = query.captureBindings()
		assertEquals(66, values.size)
		assertEquals("%" + words.drop(63).joinToString(" ") + "%", values[63])
		assertEquals(words.joinToString(" "), values[64])
		assertEquals(values.size, query.sql.count { it == '?' })
	}

	private fun androidx.room3.RoomRawQuery.captureBindings(): List<String> {
		val statement = CapturingStatement()
		getBindingFunction().invoke(statement)
		assertEquals((1..statement.values.size).toList(), statement.values.keys.toList())
		return statement.values.values.toList()
	}

	private class CapturingStatement : SQLiteStatement {
		val values = linkedMapOf<Int, String>()
		override fun bindText(index: Int, value: String) { values[index] = value }
		override fun bindBlob(index: Int, value: ByteArray): Unit = error("Expected text binding")
		override fun bindDouble(index: Int, value: Double): Unit = error("Expected text binding")
		override fun bindLong(index: Int, value: Long): Unit = error("Expected text binding")
		override fun bindNull(index: Int): Unit = error("Expected text binding")
		override fun getBlob(index: Int): ByteArray = error("No rows")
		override fun getDouble(index: Int): Double = error("No rows")
		override fun getLong(index: Int): Long = error("No rows")
		override fun getText(index: Int): String = error("No rows")
		override fun isNull(index: Int): Boolean = error("No rows")
		override fun getColumnCount(): Int = 0
		override fun getColumnName(index: Int): String = error("No columns")
		override fun getColumnType(index: Int): Int = error("No columns")
		override fun step(): Boolean = false
		override fun reset() = Unit
		override fun clearBindings() { values.clear() }
		override fun close() = Unit
	}
}
