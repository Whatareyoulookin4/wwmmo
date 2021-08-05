package au.com.codeka.warworlds.client.store

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import au.com.codeka.warworlds.common.proto.Empire
import au.com.codeka.warworlds.common.proto.SectorCoord
import au.com.codeka.warworlds.common.proto.Star
import java.io.IOException

/**
 * Specialization of [BaseStore] which stores stars. We have a custom class here because
 * we want to add some extra columns for indexing.
 */
class StarStore(private val name: String, private val helper: SQLiteOpenHelper)
    : BaseStore(name, helper) {
  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(
        "CREATE TABLE $name ("
            + "  key INTEGER PRIMARY KEY,"
            + "  sector_x INTEGER,"
            + "  sector_y INTEGER,"
            + "  my_empire INTEGER," // 1 if my empire has something on this star, 0 if not.
            + "  last_simulation INTEGER,"
            + "  name TEXT,"
            + "  value BLOB)")
    db.execSQL("CREATE INDEX IX_${name}_my_empire_name ON $name (my_empire, name)")
    db.execSQL("CREATE INDEX IX_${name}_sector ON $name (sector_x, sector_y)")
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (newVersion == 3) {
      db.execSQL("ALTER TABLE $name ADD COLUMN sector_x INTEGER")
      db.execSQL("ALTER TABLE $name ADD COLUMN sector_y INTEGER")
      db.execSQL("CREATE INDEX IX_${name}_sector ON $name (sector_x, sector_y)")

      // Update all the stars once more so that they have the correct sector_x, sector_y values.
      val cursor =
          StarCursor(
              db.query(
                  name,
                  arrayOf("value"),
                  null /* selection */,
                  null /* selectionArgs */,
                  null /* groupBy */,
                  null /* having */,
                  null /* orderBy */))
      for (star in cursor) {
        val contentValues = ContentValues()
        contentValues.put("sector_x", star.sector_x)
        contentValues.put("sector_y", star.sector_y)
        db.update(name, contentValues, "key = ?", arrayOf(star.id.toString()))
      }
    }
  }

  val myStars: StarCursor
    get() = StarCursor(helper.readableDatabase.query(
        name, arrayOf("value"),
        "my_empire = 1" /* selection */,
        null /* selectionArgs */,
        null /* groupBy */,
        null /* having */,
        null /* orderBy */))

  fun searchSectorStars(sectorCoord: SectorCoord): StarCursor {
    return StarCursor(helper.readableDatabase.query(
        name, arrayOf("value"),
        "sector_x = ${sectorCoord.x} AND sector_y = ${sectorCoord.y}" /* selection */,
        null /* selectionArgs */,
        null /* groupBy */,
        null /* having */,
        null /* orderBy */))
  }

  fun searchMyStars(search: String): StarCursor {
    val likeOperand = search.replace("%", "%%") + "%"
    return StarCursor(helper.readableDatabase.query(
        name, arrayOf("value"),
        "my_empire = 1 AND name LIKE ?" /* selection */, arrayOf(likeOperand),
        null /* groupBy */,
        null /* having */,
        null /* orderBy */))
  }

  /**
   * Gets the most recent value of last_simulation out of all our empire's stars.
   */
  val lastSimulationOfOurStar: Long?
    get() {
      val db = helper.readableDatabase
      db.query(
          false, name, arrayOf("last_simulation"), "my_empire=1",
          null, null, null, "last_simulation DESC", null).use { cursor ->
        if (cursor.moveToFirst()) {
          return cursor.getLong(0)
        }
      }
      return null
    }

  /**
   * Gets the [Star] with the given ID, or `null` if it's not found.
   */
  operator fun get(key: Long): Star? {
    val db = helper.readableDatabase
    db.query(
        false, name, arrayOf("value"), "key = ?", arrayOf(java.lang.Long.toString(key)),
        null, null, null, null).use { cursor ->
      if (cursor.moveToFirst()) {
        return try {
          Star.ADAPTER.decode(cursor.getBlob(0))
        } catch (e: IOException) {
          null
        }
      }
    }
    return null
  }

  /**
   * Puts the given value to the data store.
   */
  fun put(id: Long, star: Star, myEmpire: Empire) {
    val db = helper.writableDatabase
    val contentValues = ContentValues()
    contentValues.put("key", id)
    contentValues.put("my_empire", if (isMyStar(star, myEmpire)) 1 else 0)
    contentValues.put("last_simulation", star.last_simulation)
    contentValues.put("sector_x", star.sector_x)
    contentValues.put("sector_y", star.sector_y)
    contentValues.put("value", star.encode())
    db.insertWithOnConflict(name, null, contentValues, SQLiteDatabase.CONFLICT_REPLACE)
  }

  /** Puts all of the given values into the data store in a single transaction.  */
  fun putAll(stars: Map<Long?, Star>, myEmpire: Empire) {
    val db = helper.writableDatabase
    db.beginTransaction()
    try {
      val contentValues = ContentValues()
      for ((key, star) in stars) {
        contentValues.put("key", key)
        contentValues.put("my_empire", if (isMyStar(star, myEmpire)) 1 else 0)
        contentValues.put("last_simulation", star.last_simulation)
        contentValues.put("sector_x", star.sector_x)
        contentValues.put("sector_y", star.sector_y)
        contentValues.put("value", star.encode())
        db.insertWithOnConflict(name, null, contentValues, SQLiteDatabase.CONFLICT_REPLACE)
      }
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  companion object {
    private fun isMyStar(star: Star, myEmpire: Empire): Boolean {
      for (planet in star.planets) {
        val colony = planet.colony
        if (colony?.empire_id != null && colony.empire_id == myEmpire.id) {
          return true
        }
      }
      for (fleet in star.fleets) {
        if (fleet.empire_id != null && fleet.empire_id == myEmpire.id) {
          return true
        }
      }
      return false
    }
  }
}