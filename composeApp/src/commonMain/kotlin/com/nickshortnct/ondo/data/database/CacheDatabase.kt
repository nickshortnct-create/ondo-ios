package com.nickshortnct.ondo.data.database

import androidx.room3.AutoMigration
import androidx.room3.ColumnTypeConverters
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.DeleteTable
import androidx.room3.migration.AutoMigrationSpec
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import com.nickshortnct.ondo.data.database.dao.AlbumDao
import com.nickshortnct.ondo.data.database.dao.ArtistDao
import com.nickshortnct.ondo.data.database.dao.GenreDao
import com.nickshortnct.ondo.data.database.dao.LyricDao
import com.nickshortnct.ondo.data.database.dao.PlaylistDao
import com.nickshortnct.ondo.data.database.dao.RadioDao
import com.nickshortnct.ondo.data.database.dao.SongDao
import com.nickshortnct.ondo.data.database.dao.SyncActionDao
import com.nickshortnct.ondo.data.database.entities.AlbumEntity
import com.nickshortnct.ondo.data.database.entities.ArtistEntity
import com.nickshortnct.ondo.data.database.entities.GenreEntity
import com.nickshortnct.ondo.data.database.entities.LyricEntity
import com.nickshortnct.ondo.data.database.entities.PlaylistEntity
import com.nickshortnct.ondo.data.database.entities.PlaylistSongCrossRef
import com.nickshortnct.ondo.data.database.entities.RadioEntity
import com.nickshortnct.ondo.data.database.entities.SongEntity
import com.nickshortnct.ondo.data.database.entities.SyncActionEntity

@Database(
	version = 22,
	// Pending actions and playlist membership survive supported upgrades.
	autoMigrations = [
		AutoMigration(from = 20, to = 21, spec = CacheMigration20To21::class),
		AutoMigration(from = 21, to = 22, spec = CacheMigration21To22::class)
	],
	entities = [
		AlbumEntity::class,
		GenreEntity::class,
		PlaylistEntity::class,
		PlaylistSongCrossRef::class,
		SongEntity::class,
		ArtistEntity::class,
		RadioEntity::class,
		LyricEntity::class,
		SyncActionEntity::class
	]
)
@ColumnTypeConverters(Converters::class)
@ConstructedBy(CacheDatabaseConstructor::class)
abstract class CacheDatabase : RoomDatabase() {
	abstract fun albumDao(): AlbumDao
	abstract fun genreDao(): GenreDao
	abstract fun playlistDao(): PlaylistDao
	abstract fun songDao(): SongDao
	abstract fun artistDao(): ArtistDao
	abstract fun radioDao(): RadioDao
	abstract fun lyricDao(): LyricDao
	abstract fun syncActionDao(): SyncActionDao
}

@Suppress("KotlinNoActualForExpect")
expect object CacheDatabaseConstructor : RoomDatabaseConstructor<CacheDatabase> {
	override fun initialize(): CacheDatabase
}

// Downloads moved to DownloadDatabase; the table in the cache DB is dead weight.
@DeleteTable(tableName = "DownloadEntity")
class CacheMigration20To21 : AutoMigrationSpec

class CacheMigration21To22 : AutoMigrationSpec {
	override suspend fun onPostMigrate(connection: androidx.sqlite.SQLiteConnection) {
		backfillSearchText(connection)
	}
}

internal fun backfillSearchText(connection: androidx.sqlite.SQLiteConnection) {
	for (table in LibrarySearchTable.entries) {
		val fields = table.fields.joinToString(" || ' ' || ") { "COALESCE($it, '')" }
		connection.prepare("SELECT ${table.id}, $fields FROM ${table.entity}").use { rows ->
			connection.prepare("UPDATE ${table.entity} SET searchText = ? WHERE ${table.id} = ?").use { update ->
				while (rows.step()) {
					update.bindText(1, foldForSearch(rows.getText(1)))
					update.bindText(2, rows.getText(0))
					update.step()
					update.reset()
					update.clearBindings()
				}
			}
		}
	}
}
